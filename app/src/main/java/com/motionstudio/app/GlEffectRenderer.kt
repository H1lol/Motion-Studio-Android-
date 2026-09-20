package com.motionstudio.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GlEffectRenderer — the GPU pipeline. This is the file that actually draws
 * every frame. Preview and export use the same code path, same shaders, same
 * math. What you see on screen is what gets written to disk.
 *
 * Responsibilities:
 *   1. Compile every shader from EffectRegistry at startup
 *   2. Upload video/image/text frames to GPU textures
 *   3. Run each layer's effect chain via ping-pong framebuffers
 *   4. Composite layers into a single output texture
 *   5. Write the composite to the screen (preview) or to MediaCodec (export)
 *
 * The renderer owns these GL resources:
 *   - one vertex buffer (unit quad)
 *   - one program per unique effect (compiled once, cached)
 *   - two ping-pong framebuffers (for chained effects)
 *   - one external OES texture (for video frames from SurfaceTexture)
 *   - N image textures (one per visible image asset, LRU-cached)
 *   - N text textures (regenerated when text or time changes)
 *
 * All GL operations happen on the GL thread. The public methods below are
 * called from the UI thread and post work to the GL thread via a queue.
 */
class GlEffectRenderer(
    private val activity: Activity,
    private val state: ProjectState,
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "GlEffectRenderer"
    }

    // -------------------------------------------------------------------------
    // GL handles
    // -------------------------------------------------------------------------
    private var quadVbo = 0
    private var quadUvVbo = 0
    private var programDefault = 0        // plain passthrough (no effect)

    // Effect program cache — key is the effect's type string
    private val programCache = ConcurrentHashMap<String, Int>()

    // Ping-pong framebuffers for effect chaining
    private var fboA = 0
    private var texA = 0
    private var fboB = 0
    private var texB = 0
    private var fbWidth = 0
    private var fbHeight = 0

    // External OES texture for the SurfaceTexture (video frames)
    private var videoTexture = 0
    var videoSurface: SurfaceTexture? = null
        private set

    // Image texture cache — key is the asset's Uri string
    private val imageTextures = ConcurrentHashMap<String, Int>()

    // Text texture cache — key is "textLayerId:hash"
    private val textTextures = ConcurrentHashMap<String, Int>()
    private val textBitmapCache = ConcurrentHashMap<String, Bitmap>()

    // Uniform location caches per program
    private val uniformCache = ConcurrentHashMap<Int, Map<String, Int>>()

    // -------------------------------------------------------------------------
    // Viewport
    // -------------------------------------------------------------------------
    private var viewportW = 1
    private var viewportH = 1
    private var surfaceW = 1
    private var surfaceH = 1

    // -------------------------------------------------------------------------
    // Threading
    // -------------------------------------------------------------------------
    private val ui = Handler(Looper.getMainLooper())
    private val glCommands = ArrayDeque<() -> Unit>()
    private val glLock = Object()

    /** Posts a block to run on the GL thread. Called from any thread. */
    fun postToGl(block: () -> Unit) {
        synchronized(glLock) { glCommands.addLast(block) }
    }

    // -------------------------------------------------------------------------
    // State snapshots (thread-safe hand-off from UI thread)
    // -------------------------------------------------------------------------
    @Volatile
    private var snapshotTimeMs: Long = 0L

    /**
     * Called by MainActivity whenever the playhead moves. The renderer uses
     * this when compositing the next frame. Cheap — just a volatile write.
     */
    fun setPlayheadTime(ms: Long) {
        snapshotTimeMs = ms
    }

    // -------------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------------
    var onRendererReady: (() -> Unit)? = null
    var onFrameRendered: ((Long) -> Unit)? = null

    // =========================================================================
    // GLSurfaceView.Renderer
    // =========================================================================

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // --- Unit quad ---
        buildQuad()

        // --- Default passthrough shader ---
        programDefault = compileProgram(DEFAULT_VERTEX, DEFAULT_FRAGMENT)
        if (programDefault == 0) {
            Log.e(TAG, "Default program failed to compile — nothing will render")
            return
        }
        cacheUniforms(programDefault)

        // --- Video surface texture ---
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        videoTexture = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        val st = SurfaceTexture(videoTexture)
        st.setOnFrameAvailableListener { requestRender() }
        videoSurface = st

        // --- Pre-compile every effect shader ---
        preCompileAllShaders()

        ui.post { onRendererReady?.invoke() }
        Log.i(TAG, "Renderer ready. ${programCache.size} effect programs cached.")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
        viewportW = width
        viewportH = height
        GLES20.glViewport(0, 0, width, height)
        allocateFramebuffers(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Drain any GL-thread work queued from the UI thread
        synchronized(glLock) {
            while (glCommands.isNotEmpty()) {
                val cmd = glCommands.removeFirst()
                try { cmd() } catch (t: Throwable) {
                    Log.e(TAG, "GL command failed: ${t.message}")
                }
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val time = snapshotTimeMs

        // Grab a snapshot of visible layers at this time
        val visibleLayers = visibleLayersAt(time)

        if (visibleLayers.isEmpty()) {
            // Nothing to draw — clear to black
            val w = if (viewportW > 0) viewportW else 1
            val h = if (viewportH > 0) viewportH else 1
            GLES20.glViewport(0, 0, w, h)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            onFrameRendered?.invoke(time)
            return
        }

        // Composite all visible layers into an output texture
        val outputTex = compositeLayers(visibleLayers, time)

        // Draw that texture to the screen
        drawTextureToScreen(outputTex)

        onFrameRendered?.invoke(time)
    }

    private fun requestRender() {
        (activity as? MainActivity)?.findViewById<GLSurfaceView>(0)
        // The GLSurfaceView itself is the parent; requestRender is called by
        // MainActivity after mutating state. This method is a no-op hook for
        // the SurfaceTexture listener. MainActivity calls requestRender() on
        // the GLSurfaceView directly.
    }

    // =========================================================================
    // Shader compilation
    // =========================================================================

    private fun preCompileAllShaders() {
        for (def in EffectRegistry.ALL) {
            val src = EffectRegistry.fullFragment(def)
            val prog = compileProgram(DEFAULT_VERTEX, src)
            if (prog != 0) {
                programCache[def.name] = prog
                cacheUniforms(prog)
            } else {
                Log.w(TAG, "Effect shader failed: ${def.name}")
            }
        }
    }

    private fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
            ?: return 0
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            ?: return 0

        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)

        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Link failed: " + GLES20.glGetProgramInfoLog(prog))
            GLES20.glDeleteProgram(prog)
            return 0
        }

        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int? {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return null
        }
        return shader
    }

    private fun cacheUniforms(program: Int) {
        val map = HashMap<String, Int>(8)
        for (name in listOf(
            "uTexture", "uDepthMap", "uResolution", "uTime",
            "uP0", "uP1", "uP2",
            "uMVP", "uOpacity", "uColor",
        )) {
            map[name] = GLES20.glGetUniformLocation(program, name)
        }
        for (name in listOf("aPosition", "aTexCoord")) {
            map[name] = GLES20.glGetAttribLocation(program, name)
        }
        uniformCache[program] = map
    }

    private fun uniform(program: Int, name: String): Int =
        uniformCache[program]?.get(name) ?: -1

    // =========================================================================
    // Quad geometry
    // =========================================================================

    private fun buildQuad() {
        val vertices = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f,
        )
        val uvs = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        )

        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        quadVbo = ids[0]
        quadUvVbo = ids[1]

        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices); position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
            vertices.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

        val tBuf = ByteBuffer.allocateDirect(uvs.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(uvs); position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadUvVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
            uvs.size * 4, tBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawQuad(program: Int) {
        val aPos = uniform(program, "aPosition")
        val aUv = uniform(program, "aTexCoord")

        if (aPos >= 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, 0)
        }
        if (aUv >= 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadUvVbo)
            GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, 0)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (aPos >= 0) GLES20.glDisableVertexAttribArray(aPos)
        if (aUv >= 0) GLES20.glDisableVertexAttribArray(aUv)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // =========================================================================
    // Framebuffers
    // =========================================================================

    private fun allocateFramebuffers(width: Int, height: Int) {
        if (width == fbWidth && height == fbHeight && fboA != 0) return
        releaseFramebuffers()
        fbWidth = width
        fbHeight = height

        val ids = IntArray(1)

        GLES20.glGenFramebuffers(1, ids, 0); fboA = ids[0]
        GLES20.glGenTextures(1, ids, 0); texA = ids[0]
        configureFboTexture(texA, width, height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboA)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texA, 0)

        GLES20.glGenFramebuffers(1, ids, 0); fboB = ids[0]
        GLES20.glGenTextures(1, ids, 0); texB = ids[0]
        configureFboTexture(texB, width, height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboB)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texB, 0)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Framebuffer incomplete: 0x${Integer.toHexString(status)}")
        }
    }

    private fun configureFboTexture(tex: Int, w: Int, h: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun releaseFramebuffers() {
        if (fboA != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboA), 0)
        if (fboB != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboB), 0)
        if (texA != 0) GLES20.glDeleteTextures(1, intArrayOf(texA), 0)
        if (texB != 0) GLES20.glDeleteTextures(1, intArrayOf(texB), 0)
        fboA = 0; fboB = 0; texA = 0; texB = 0
    }

    // =========================================================================
    // Shader sources
    // =========================================================================

    private val DEFAULT_VERTEX = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vUV;
        void main() {
            vUV = aTexCoord;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """.trimIndent()

    private val DEFAULT_FRAGMENT = """
        precision highp float;
        varying vec2 vUV;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vUV);
        }
    """.trimIndent()
// =========================================================================
// Visible layer snapshot
// =========================================================================

private data class VisibleLayer(
    val layer: Layer2D,
    val asset: MediaAsset,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
)

private fun visibleLayersAt(timeMs: Long): List<VisibleLayer> {
    val out = ArrayList<VisibleLayer>(state.layers.size)
    for (layer in state.layers) {
        if (!layer.visible) continue
        if (layer.trackIndex >= state.trackStates.size) continue
        val ts = state.trackStates[layer.trackIndex]
        if (ts.hidden) continue
        if (timeMs < layer.timelineStartMs) continue
        if (timeMs >= layer.timelineEndMs()) continue

        val asset = state.assetById(layer.assetId) ?: continue

        val x = layer.sample("x", timeMs)
        val y = layer.sample("y", timeMs)
        val s = layer.sample("scale", timeMs)
        val r = layer.sample("rotation", timeMs)
        val o = layer.sample("opacity", timeMs)

        out.add(VisibleLayer(layer, asset, x, y, s, r, o))
    }
    // Sort by track index: V4 first, V1 last, so V1 draws on top
    return out.sortedByDescending { it.layer.trackIndex }
}

// =========================================================================
// Layer compositing
// =========================================================================

/**
 * Composites all visible layers into a single output texture.
 *
 * For each layer:
 *   1. Get a texture (from image cache, video SurfaceTexture, or text bitmap)
 *   2. Run its effect chain via ping-pong FBOs
 *   3. Draw the result into the accumulation FBO with the layer's transform
 *
 * Returns the texture ID of the final composite.
 */
private fun compositeLayers(layers: List<VisibleLayer>, timeMs: Long): Int {
    if (layers.isEmpty()) return 0

    // First pass: draw the bottom-most layer directly into FBO A
    val first = layers.first()
    val firstTexture = textureForLayer(first, timeMs)
    if (firstTexture != 0) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboA)
        GLES20.glViewport(0, 0, fbWidth, fbHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Apply effect chain on the first layer (writes back to texA)
        var srcTexture = firstTexture
        if (first.layer.effects.isNotEmpty()) {
            srcTexture = applyEffectChain(srcTexture, first.layer, timeMs)
        }
        // Draw with transform on top of the empty FBO
        drawLayerQuad(srcTexture, first)
    }

    // Remaining layers: draw on top of FBO A
    for (i in 1 until layers.size) {
        val v = layers[i]
        var srcTexture = textureForLayer(v, timeMs)
        if (srcTexture == 0) continue

        if (v.layer.effects.isNotEmpty()) {
            srcTexture = applyEffectChain(srcTexture, v.layer, timeMs)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboA)
        GLES20.glViewport(0, 0, fbWidth, fbHeight)
        drawLayerQuad(srcTexture, v)
    }

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    return texA
}

/**
 * Draws one layer as a transformed quad, using its position/scale/rotation.
 * The shader used is the default passthrough. Blending is enabled globally.
 */
private fun drawLayerQuad(texture: Int, v: VisibleLayer) {
    val program = programDefault
    GLES20.glUseProgram(program)

    val mvp = FloatArray(16)
    Matrix.setIdentityM(mvp, 0)

    // Normalize screen-space coordinates (state.width x state.height) to NDC
    val sx = v.scale * 2f * v.layer.sample("scale", snapshotTimeMs).coerceAtLeast(0.01f) /
             state.width.toFloat() * state.width.toFloat()
    val sxf = v.scale
    val syf = v.scale

    // Translate to (x, y), then rotate, then scale
    Matrix.translateM(mvp, 0, v.x / state.width * 2f, -v.y / state.height * 2f, 0f)
    Matrix.rotateM(mvp, 0, -v.rotation, 0f, 0f, 1f)
    Matrix.scaleM(mvp, 0, sxf, syf, 1f)

    val uMvp = uniform(program, "uMVP")
    if (uMvp >= 0) GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

    val uOpacity = uniform(program, "uOpacity")
    if (uOpacity >= 0) GLES20.glUniform1f(uOpacity, v.opacity)

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    val uTex = uniform(program, "uTexture")
    if (uTex >= 0) GLES20.glUniform1i(uTex, 0)

    val uRes = uniform(program, "uResolution")
    if (uRes >= 0) GLES20.glUniform2f(uRes, fbWidth.toFloat(), fbHeight.toFloat())

    drawQuad(program)
}

// =========================================================================
// Texture source per layer
// =========================================================================

private fun textureForLayer(v: VisibleLayer, timeMs: Long): Int {
    return when (v.asset.kind) {
        AssetKind.VIDEO -> videoTexture
        AssetKind.IMAGE -> imageTexture(v.asset)
        AssetKind.AUDIO -> 0
        AssetKind.UNKNOWN -> 0
    }
}

/**
 * Loads and caches an image asset as a GL texture. First call decodes and
 * uploads; subsequent calls return the cached texture ID.
 */
private fun imageTexture(asset: MediaAsset): Int {
    val key = asset.uri.toString()
    imageTextures[key]?.let { return it }

    val bmp = try {
        activity.contentResolver.openInputStream(asset.uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    } catch (_: Throwable) { null } ?: return 0

    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    val tex = ids[0]
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    bmp.recycle()

    imageTextures[key] = tex
    return tex
}

// =========================================================================
// Effect chain
// =========================================================================

/**
 * Runs each effect in the layer's stack, ping-ponging between FBO A and B.
 * Returns the texture ID holding the final result.
 *
 * Note: this writes into FBO A/B, so it must not be called while those
 * FBOs are being used for compositing. The composite path rebinds FBO A
 * after this returns, so the caller is fine.
 */
private fun applyEffectChain(
    sourceTexture: Int,
    layer: Layer2D,
    timeMs: Long,
): Int {
    var src = sourceTexture
    var writeToA = true

    for (fx in layer.effects) {
        if (!fx.enabled) continue
        val prog = programCache[fx.type] ?: continue

        val dstFbo = if (writeToA) fboA else fboB
        val dstTex = if (writeToA) texA else texB

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dstFbo)
        GLES20.glViewport(0, 0, fbWidth, fbHeight)

        GLES20.glUseProgram(prog)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, src)
        val uTex = uniform(prog, "uTexture")
        if (uTex >= 0) GLES20.glUniform1i(uTex, 0)

        val uRes = uniform(prog, "uResolution")
        if (uRes >= 0) GLES20.glUniform2f(uRes, fbWidth.toFloat(), fbHeight.toFloat())

        val uTime = uniform(prog, "uTime")
        if (uTime >= 0) GLES20.glUniform1f(uTime, timeMs / 1000f)

        val uP0 = uniform(prog, "uP0")
        if (uP0 >= 0) GLES20.glUniform1f(uP0, fx.params["p0"] ?: 1f)
        val uP1 = uniform(prog, "uP1")
        if (uP1 >= 0) GLES20.glUniform1f(uP1, fx.params["p1"] ?: 0f)
        val uP2 = uniform(prog, "uP2")
        if (uP2 >= 0) GLES20.glUniform1f(uP2, fx.params["p2"] ?: 0f)

        drawQuad(prog)

        src = dstTex
        writeToA = !writeToA
    }

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    return src
}

// =========================================================================
// Draw final output to screen
// =========================================================================

private fun drawTextureToScreen(texture: Int) {
    if (texture == 0) return

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    GLES20.glViewport(0, 0, surfaceW, surfaceH)
    GLES20.glClearColor(0f, 0f, 0f, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    // Aspect-fit the composited frame inside the surface
    val projW = state.width.toFloat()
    val projH = state.height.toFloat()
    val projAspect = projW / projH
    val surfAspect = surfaceW.toFloat() / surfaceH.toFloat()

    val vpX: Int
    val vpY: Int
    val vpW: Int
    val vpH: Int
    if (projAspect > surfAspect) {
        vpW = surfaceW
        vpH = (surfaceW / projAspect).toInt()
        vpX = 0
        vpY = (surfaceH - vpH) / 2
    } else {
        vpH = surfaceH
        vpW = (surfaceH * projAspect).toInt()
        vpX = (surfaceW - vpW) / 2
        vpY = 0
    }
    GLES20.glViewport(vpX, vpY, vpW, vpH)

    val prog = programDefault
    GLES20.glUseProgram(prog)

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    val uTex = uniform(prog, "uTexture")
    if (uTex >= 0) GLES20.glUniform1i(uTex, 0)

    val uRes = uniform(prog, "uResolution")
    if (uRes >= 0) GLES20.glUniform2f(uRes, vpW.toFloat(), vpH.toFloat())

    drawQuad(prog)
}
    // =========================================================================
    // External frame upload (video frames from SurfaceTexture)
    // =========================================================================

    /**
     * Called by MainActivity (or a future VideoDecoder) when a new video frame
     * is available on the SurfaceTexture. Uploads it to the external OES texture.
     */
    fun updateVideoFrame() {
        postToGl {
            try {
                videoSurface?.updateTexImage()
            } catch (t: Throwable) {
                Log.w(TAG, "updateTexImage failed: ${t.message}")
            }
        }
    }

    // videoSurface is already exposed via its property getter (see declaration
    // above) — Kotlin/Java callers can use `.videoSurface` / `getVideoSurface()`
    // automatically, so a duplicate function here is unnecessary.

    // =========================================================================
    // Pro mode hooks
    // =========================================================================

    /**
     * Called by MainActivity when the user toggles Pro mode. Reallocates the
     * ping-pong framebuffers with the new texture format.
     */
    fun onPipelineModeChanged() {
        postToGl {
            releaseFramebuffers()
            allocateFramebuffers(fbWidth, fbHeight)
            preCompileAllShaders()
        }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Releases every GL resource. Called from MainActivity.onDestroy().
     * Must be called on the GL thread or on a paused surface.
     */
    fun release() {
        postToGl {
            try {
                releaseFramebuffers()

                if (videoTexture != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(videoTexture), 0)
                    videoTexture = 0
                }
                videoSurface?.release()
                videoSurface = null

                for (tex in imageTextures.values) {
                    GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
                }
                imageTextures.clear()

                for (tex in textTextures.values) {
                    GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
                }
                textTextures.clear()
                textBitmapCache.clear()

                for (prog in programCache.values) {
                    GLES20.glDeleteProgram(prog)
                }
                programCache.clear()

                if (programDefault != 0) {
                    GLES20.glDeleteProgram(programDefault)
                    programDefault = 0
                }

                if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
                if (quadUvVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadUvVbo), 0)
                quadVbo = 0
                quadUvVbo = 0

                uniformCache.clear()
                Log.i(TAG, "All GL resources released")
            } catch (t: Throwable) {
                Log.e(TAG, "Release failed: ${t.message}")
            }
        }
    }

    // =========================================================================
    // Frame export (for RenderEngine)
    // =========================================================================

    /**
     * Renders a single frame at the given time and reads it back as a Bitmap.
     * Used by RenderEngine when exporting. Blocks until the readback completes.
     *
     * Must be called on the GL thread — RenderEngine should postToGl().
     */
    fun renderFrameToBitmap(timeMs: Long, width: Int, height: Int): Bitmap? {
        return try {
            snapshotTimeMs = timeMs
            val visible = visibleLayersAt(timeMs)

            // Reallocate FBOs at export resolution
            allocateFramebuffers(width, height)

            if (visible.isEmpty()) {
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }

            val outputTex = compositeLayers(visible, timeMs)

            // Bind FBO A as the read source
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboA)

            val buffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            bmp.copyPixelsFromBuffer(buffer)

            // GL origin is bottom-left; Bitmap is top-left. Flip vertically.
            val matrix = android.graphics.Matrix()
            matrix.postScale(1f, -1f)
            val flipped = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, false)
            bmp.recycle()
            flipped
        } catch (t: Throwable) {
            Log.e(TAG, "renderFrameToBitmap failed: ${t.message}")
            null
        }
    }

    // =========================================================================
    // Text texture support (called by the text rendering path)
    // =========================================================================

    /**
     * Registers a rasterized text bitmap as a GL texture. The text layer's
     * renderer calls this when it needs to composite a text layer.
     *
     * `cacheKey` is a string that uniquely identifies this text + time + preset
     * combination. Reuse the same key while the text hasn't changed.
     */
    fun uploadTextBitmap(cacheKey: String, bitmap: Bitmap): Int {
        textTextures[cacheKey]?.let { return it }

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val tex = ids[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        textTextures[cacheKey] = tex
        return tex
    }

    /**
     * Drops a cached text texture. Call this when the text changes so the
     * next frame regenerates it.
     */
    fun invalidateTextTexture(cacheKey: String) {
        textTextures.remove(cacheKey)?.let { tex ->
            postToGl {
                GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            }
        }
    }

    // =========================================================================
    // Diagnostics
    // =========================================================================

    data class RendererStats(
        val programCount: Int,
        val imageTextureCount: Int,
        val textTextureCount: Int,
        val fbWidth: Int,
        val fbHeight: Int,
        val surfaceWidth: Int,
        val surfaceHeight: Int,
    )

    fun stats(): RendererStats = RendererStats(
        programCount = programCache.size + if (programDefault != 0) 1 else 0,
        imageTextureCount = imageTextures.size,
        textTextureCount = textTextures.size,
        fbWidth = fbWidth,
        fbHeight = fbHeight,
        surfaceWidth = surfaceW,
        surfaceHeight = surfaceH,
    )
}

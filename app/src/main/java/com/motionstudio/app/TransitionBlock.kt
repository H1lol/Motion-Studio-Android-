package com.motionstudio.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * TransitionBlock — the runtime that drives TransitionRegistry.
 *
 * Two jobs:
 *   1. Provide `indexOfDefault()` — MainActivity calls this when the user
 *      taps "Add transition" without picking a specific one.
 *
 *   2. Provide a self-contained offscreen GL renderer that generates
 *      transition preview thumbnails. Self-contained means it doesn't
 *      touch GlEffectRenderer's GL context — it makes its own EGL context
 *      via PBuffer, runs one render pass per transition, reads back to a
 *      Bitmap, and tears down.
 *
 * The preview thumbnails are generated on demand by TransitionPicker when
 * the picker overlay opens. They're cached in memory so opening the picker
 * a second time is instant.
 */
object TransitionBlock {

    private const val TAG = "TransitionBlock"

    /** Default transition index. Used by MainActivity.requestAddTransition. */
    fun indexOfDefault(): Int = 0

    /**
     * Returns the full catalog. Convenience passthrough so callers don't
     * need to import both TransitionRegistry and TransitionBlock.
     */
    fun catalog(): List<TransitionRegistry.TransitionDef> = TransitionRegistry.ALL

    fun count(): Int = TransitionRegistry.COUNT

    // =========================================================================
    // Sample frames — what the transition renders between
    // =========================================================================

    /**
     * Generates a pair of sample bitmaps for preview rendering.
     * "From" is a warm gradient, "to" is a cool gradient, both with a
     * recognizable marker in the center so motion is visible.
     */
    fun generateSamplePair(width: Int, height: Int): Pair<Bitmap, Bitmap> {
        val from = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val to = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val c1 = Canvas(from)
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG)
        p1.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(230, 100, 60), Color.rgb(255, 200, 100),
            Shader.TileMode.CLAMP,
        )
        c1.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p1)
        val p1b = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 20, 10)
            style = Paint.Style.STROKE
            strokeWidth = height * 0.04f
        }
        c1.drawCircle(width / 2f, height / 2f, height * 0.25f, p1b)

        val c2 = Canvas(to)
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
        p2.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(60, 100, 230), Color.rgb(120, 220, 255),
            Shader.TileMode.CLAMP,
        )
        c2.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p2)
        val p2b = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 20, 40)
            style = Paint.Style.STROKE
            strokeWidth = height * 0.04f
        }
        c2.drawRect(
            width * 0.25f, height * 0.25f,
            width * 0.75f, height * 0.75f,
            p2b,
        )

        return from to to
    }

    // =========================================================================
    // Offscreen GL renderer — for thumbnails
    // =========================================================================

    /**
     * A self-contained EGL renderer. Creates its own context, compiles the
     * shader on demand, renders one frame, reads it back, releases.
     *
     * Not thread-safe. Call from a single background thread.
     */
    class ThumbnailRenderer {

        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var config: EGLConfig? = null

        private var programCache = ConcurrentHashMap<String, Int>()
        private var quadVbo = 0
        private var quadUvVbo = 0
        private var fbo = 0
        private var fboTex = 0
        private var texW = 0
        private var texH = 0

        var initialized = false
            private set

        fun initialize(width: Int, height: Int): Boolean {
            if (initialized) return true
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (display == EGL14.EGL_NO_DISPLAY) return false

                val version = IntArray(2)
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false

                val configAttrs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, numConfigs, 0)) return false
                if (numConfigs[0] == 0) return false
                config = configs[0]

                val contextAttrs = intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE,
                )
                context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
                if (context == EGL14.EGL_NO_CONTEXT) return false

                val surfaceAttrs = intArrayOf(
                    EGL14.EGL_WIDTH, width,
                    EGL14.EGL_HEIGHT, height,
                    EGL14.EGL_NONE,
                )
                surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttrs, 0)
                if (surface == EGL14.EGL_NO_SURFACE) return false

                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false

                // Build quad
                val verts = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
                val uvs = floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f)

                val vboIds = IntArray(2)
                GLES20.glGenBuffers(2, vboIds, 0)
                quadVbo = vboIds[0]
                quadUvVbo = vboIds[1]

                val vBuf = ByteBuffer.allocateDirect(verts.size * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    .apply { put(verts); position(0) }
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

                val tBuf = ByteBuffer.allocateDirect(uvs.size * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    .apply { put(uvs); position(0) }
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadUvVbo)
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, uvs.size * 4, tBuf, GLES20.GL_STATIC_DRAW)

                // FBO
                ensureFbo(width, height)

                initialized = true
                return true
            } catch (t: Throwable) {
                Log.e(TAG, "EGL init failed: ${t.message}")
                release()
                return false
            }
        }

        private fun ensureFbo(w: Int, h: Int) {
            if (fbo != 0 && texW == w && texH == h) return
            releaseFbo()

            val ids = IntArray(1)
            GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
            GLES20.glGenTextures(1, ids, 0); fboTex = ids[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTex, 0,
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            texW = w
            texH = h
        }

        private fun releaseFbo() {
            if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
            fbo = 0; fboTex = 0
        }

        /**
         * Renders one transition between two bitmaps at a given progress and
         * returns the result as a new Bitmap.
         */
        fun renderTransition(
            transitionName: String,
            from: Bitmap,
            to: Bitmap,
            progress: Float,
            width: Int,
            height: Int,
        ): Bitmap? {
            if (!initialized) return null

            val program = getOrCompile(transitionName) ?: return null

            ensureFbo(width, height)

            val fromTex = uploadBitmap(from)
            val toTex = uploadBitmap(to)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fromTex)
            val uFrom = GLES20.glGetUniformLocation(program, "uFrom")
            if (uFrom >= 0) GLES20.glUniform1i(uFrom, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toTex)
            val uTo = GLES20.glGetUniformLocation(program, "uTo")
            if (uTo >= 0) GLES20.glUniform1i(uTo, 1)

            val uProgress = GLES20.glGetUniformLocation(program, "progress")
            if (uProgress >= 0) GLES20.glUniform1f(uProgress, progress)

            val uRes = GLES20.glGetUniformLocation(program, "uResolution")
            if (uRes >= 0) GLES20.glUniform2f(uRes, width.toFloat(), height.toFloat())

            val uTime = GLES20.glGetUniformLocation(program, "uTime")
            if (uTime >= 0) GLES20.glUniform1f(uTime, progress * 1.5f)

            val aPos = GLES20.glGetAttribLocation(program, "aPosition")
            val aUv = GLES20.glGetAttribLocation(program, "aTexCoord")

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

            // Readback
            val buf = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            GLES20.glDeleteTextures(1, intArrayOf(fromTex), 0)
            GLES20.glDeleteTextures(1, intArrayOf(toTex), 0)

            // Convert to bitmap and flip vertically
            val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buf.rewind()
            raw.copyPixelsFromBuffer(buf)
            val m = Matrix().apply { postScale(1f, -1f) }
            val flipped = Bitmap.createBitmap(raw, 0, 0, width, height, m, false)
            raw.recycle()
            return flipped
        }

        private fun getOrCompile(name: String): Int? {
            programCache[name]?.let { return it }
            val def = TransitionRegistry.byName(name) ?: return null
            val frag = TransitionRegistry.fullFragment(def)
            val vs = """
                attribute vec2 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vUV;
                void main() {
                    vUV = aTexCoord;
                    gl_Position = vec4(aPosition, 0.0, 1.0);
                }
            """.trimIndent()

            val vsShader = compile(GLES20.GL_VERTEX_SHADER, vs) ?: return null
            val fsShader = compile(GLES20.GL_FRAGMENT_SHADER, frag) ?: return null
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vsShader)
            GLES20.glAttachShader(prog, fsShader)
            GLES20.glLinkProgram(prog)

            val status = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e(TAG, "Link failed for $name: " + GLES20.glGetProgramInfoLog(prog))
                GLES20.glDeleteProgram(prog)
                return null
            }
            GLES20.glDeleteShader(vsShader)
            GLES20.glDeleteShader(fsShader)
            programCache[name] = prog
            return prog
        }

        private fun compile(type: Int, src: String): Int? {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val st = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
            if (st[0] == 0) {
                Log.e(TAG, "Shader failed: " + GLES20.glGetShaderInfoLog(s))
                GLES20.glDeleteShader(s)
                return null
            }
            return s
        }

        private fun uploadBitmap(bmp: Bitmap): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val tex = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            return tex
        }

        fun release() {
            try {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT,
                    )
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
            } catch (_: Throwable) {}
            display = EGL14.EGL_NO_DISPLAY
            context = EGL14.EGL_NO_CONTEXT
            surface = EGL14.EGL_NO_SURFACE
            programCache.clear()
            quadVbo = 0
            quadUvVbo = 0
            fbo = 0
            fboTex = 0
            initialized = false
        }
    }
}

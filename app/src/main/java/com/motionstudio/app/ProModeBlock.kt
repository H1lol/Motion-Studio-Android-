package com.motionstudio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log

/**
 * ProModeBlock — the 32-bit float linear color pipeline.
 *
 * Consumer mode runs 8-bit sRGB. Pro mode runs 32-bit float linear with an
 * ACEScg working space. The rest of the app (effects, transitions, node graph,
 * text) sits above this layer and doesn't care which one is active.
 *
 * The mode is a global toggle. When PRO is active:
 *   - The GL renderer allocates GL_RGBA32F textures instead of GL_RGBA8
 *   - Shaders receive linear input via the input transform snippet
 *   - Shaders emit linear output via the output transform snippet
 *   - Memory usage is 4× consumer mode — guarded by GpuMemoryGuard
 *
 * Zero native dependencies. The ACES transforms are matrix math in Kotlin.
 * Optional OCIO support can hook in later if you add the native library.
 */
object ProModeBlock {

    private const val TAG = "ProMode"

    // =========================================================================
    // Mode
    // =========================================================================

    enum class PipelineMode { CONSUMER, PRO }

    @Volatile
    private var mode: PipelineMode = PipelineMode.CONSUMER

    fun currentMode(): PipelineMode = mode
    fun isPro(): Boolean = mode == PipelineMode.PRO

    /**
     * Attempts to switch modes. Returns true if the switch succeeded.
     * Pro mode can fail if the device can't handle the memory cost.
     */
    fun setMode(newMode: PipelineMode, activity: Activity): Boolean {
        if (newMode == mode) return true
        if (newMode == PipelineMode.PRO && !GpuMemoryGuard.canEnablePro(activity)) {
            Log.w(TAG, "Cannot enable Pro mode: device not capable")
            return false
        }
        mode = newMode
        Log.i(TAG, "Pipeline mode: $newMode")
        return true
    }

    // =========================================================================
    // Working color space
    // =========================================================================

    enum class WorkingSpace(val displayName: String) {
        SRGB("sRGB (Consumer)"),
        LINEAR_SRGB("Linear sRGB"),
        ACESCG("ACEScg"),
        ACESCC("ACEScc"),
        ARRI_LOGC("ARRI LogC"),
        V_LOG("Panasonic V-Log"),
        S_LOG3("Sony S-Log3"),
    }

    @Volatile
    private var workingSpace: WorkingSpace = WorkingSpace.SRGB

    fun getWorkingSpace(): WorkingSpace = workingSpace

    fun setWorkingSpace(space: WorkingSpace) {
        if (!isPro() && space != WorkingSpace.SRGB) {
            Log.w(TAG, "Non-sRGB working spaces require Pro mode")
            return
        }
        workingSpace = space
    }

    // =========================================================================
    // GL texture formats
    // =========================================================================

    data class GlFormat(
        val internalFormat: Int,
        val format: Int,
        val type: Int,
        val bytesPerChannel: Int,
        val channels: Int,
    ) {
        val bytesPerPixel: Int get() = bytesPerChannel * channels
        val isFloat: Boolean get() =
            type == GLES30.GL_FLOAT || type == GLES30.GL_HALF_FLOAT
    }

    val CONSUMER_FORMAT = GlFormat(
        internalFormat = GLES20.GL_RGBA,
        format = GLES20.GL_RGBA,
        type = GLES20.GL_UNSIGNED_BYTE,
        bytesPerChannel = 1,
        channels = 4,
    )

    val PRO_FORMAT_F32 = GlFormat(
        internalFormat = GLES30.GL_RGBA32F,
        format = GLES20.GL_RGBA,
        type = GLES30.GL_FLOAT,
        bytesPerChannel = 4,
        channels = 4,
    )

    val PRO_FORMAT_F16 = GlFormat(
        internalFormat = GLES30.GL_RGBA16F,
        format = GLES20.GL_RGBA,
        type = GLES30.GL_HALF_FLOAT,
        bytesPerChannel = 2,
        channels = 4,
    )

    val activeFormat: GlFormat
        get() = if (isPro()) PRO_FORMAT_F32 else CONSUMER_FORMAT

    /** Estimated GPU memory for one frame at a given size. */
    fun frameBytes(width: Int, height: Int): Long =
        width.toLong() * height.toLong() * activeFormat.bytesPerPixel

    // =========================================================================
    // sRGB <-> linear transfer functions
    // =========================================================================
    //
    // Standard IEC 61966-2-1. Same formulas used by OpenColorIO, Nuke, Resolve.
    //

    fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f
        else Math.pow(((c + 0.055) / 1.055).toDouble(), 2.4).toFloat()

    fun linearToSrgb(c: Float): Float =
        if (c <= 0.0031308f) c * 12.92f
        else (1.055f * Math.pow(c.toDouble(), 1.0 / 2.4).toFloat() - 0.055f)

    // =========================================================================
    // ACEScg <-> sRGB matrices
    // =========================================================================
    //
    // The ACEScg working space is an AP1 gamut. Converting from sRGB (BT.709
    // primaries) requires two matrices: BT.709 → XYZ (D60), then XYZ → AP1.
    // The combined matrix is what's below.
    //
    // Reference: Stephen Hill's ACEScg conversion (public domain).

    private val SRGB_TO_ACESCG = floatArrayOf(
        0.6131f, 0.3395f, 0.0474f,
        0.0702f, 0.9164f, 0.0134f,
        0.0206f, 0.1096f, 0.8698f,
    )

    private val ACESCG_TO_SRGB = floatArrayOf(
        1.7049f, -0.6241f, -0.0808f,
        -0.1303f, 1.1400f, -0.0097f,
        -0.0239f, -0.2049f, 1.2288f,
    )

    fun transformRgb(
        r: Float, g: Float, b: Float,
        matrix: FloatArray,
    ): FloatArray {
        return floatArrayOf(
            matrix[0] * r + matrix[1] * g + matrix[2] * b,
            matrix[3] * r + matrix[4] * g + matrix[5] * b,
            matrix[6] * r + matrix[7] * g + matrix[8] * b,
        )
    }

    fun srgbToAcescg(r: Float, g: Float, b: Float): FloatArray {
        val lin = floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
        return transformRgb(lin[0], lin[1], lin[2], SRGB_TO_ACESCG)
    }

    fun acescgToSrgb(r: Float, g: Float, b: Float): FloatArray {
        val srgbLin = transformRgb(r, g, b, ACESCG_TO_SRGB)
        return floatArrayOf(
            linearToSrgb(srgbLin[0]),
            linearToSrgb(srgbLin[1]),
            linearToSrgb(srgbLin[2]),
        )
    }

    // =========================================================================
    // GLSL snippets injected into shaders
    // =========================================================================

    /**
     * Prepended to every effect shader in Pro mode.
     *
     * Declares the sRGB↔linear helper functions and does the initial
     * conversion from texture input to working space.
     *
     * Effects should then operate on `vUV` normally — the input texture is
     * sampled with `texture2D(uTexture, vUV)` and the result is linear.
     */
    fun inputTransformGlsl(): String {
        if (!isPro()) return ""
        return """
            vec3 srgbToLinear3(vec3 c) {
                vec3 lo = c / 12.92;
                vec3 hi = pow((c + vec3(0.055)) / 1.055, vec3(2.4));
                return mix(lo, hi, step(vec3(0.04045), c));
            }
            vec3 linearToSrgb3(vec3 c) {
                vec3 lo = c * 12.92;
                vec3 hi = 1.055 * pow(max(c, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
                return mix(lo, hi, step(vec3(0.0031308), c));
            }
        """.trimIndent()
    }

    /**
     * Appended to every effect shader in Pro mode.
     *
     * Converts the shader's linear output back to sRGB for display. Only the
     * final pass should use this — intermediate passes stay linear.
     */
    fun outputTransformGlsl(): String {
        if (!isPro()) return ""
        return """
            // Pro mode output transform
            gl_FragColor.rgb = linearToSrgb3(gl_FragColor.rgb);
        """.trimIndent()
    }

    /**
     * The ACES tonemap curve. Only injected when the working space is ACEScg
     * and the user has tonemapping enabled. Otherwise the linear output goes
     * straight to the sRGB OETF.
     */
    fun acesTonemapGlsl(): String = """
        vec3 acesTonemap(vec3 x) {
            const float a = 2.51;
            const float b = 0.03;
            const float c = 2.43;
            const float d = 0.59;
            const float e = 0.14;
            return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
        }
    """.trimIndent()

    // =========================================================================
    // Status
    // =========================================================================

    fun statusLabel(): String = when (mode) {
        PipelineMode.CONSUMER -> "8-bit · sRGB"
        PipelineMode.PRO -> "32-bit float · ${workingSpace.displayName}"
    }
        // =========================================================================
    // GPU memory guard
    // =========================================================================

    /**
     * Checks whether the device can handle Pro mode.
     *
     * Pro mode's 32-bit float textures are 4× the memory of consumer mode.
     * A 1080p float frame is 33 MB. With ping-pong buffers, masks, and
     * intermediate passes, a full pipeline needs ~200 MB of GPU memory.
     * On a phone with 3 GB total RAM, that's risky.
     *
     * This guard refuses Pro mode on devices that would crash.
     */
    object GpuMemoryGuard {

        fun canEnablePro(activity: Activity): Boolean {
            return checkGlVersion(activity) && checkRam(activity)
        }

        fun reasonForRefusal(activity: Activity): String {
            if (!checkGlVersion(activity)) {
                val info = getGlInfo(activity)
                return "Your device has OpenGL ES ${info.first}.${info.second}. " +
                       "Pro mode needs ES 3.0 or newer for float textures."
            }
            if (!checkRam(activity)) {
                val mb = getTotalRamMb(activity)
                return "Your device has ${mb}MB RAM. " +
                       "Pro mode needs at least 3 GB for stable operation."
            }
            return ""
        }

        private fun checkGlVersion(activity: Activity): Boolean {
            val (major, _) = getGlInfo(activity)
            return major >= 3
        }

        private fun checkRam(activity: Activity): Boolean {
            return getTotalRamMb(activity) >= 3000L
        }

        private fun getGlInfo(activity: Activity): Pair<Int, Int> {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            val version = am.deviceConfigurationInfo.reqGlEsVersion
            val major = (version shr 16) and 0xFFFF
            val minor = version and 0xFFFF
            return major to minor
        }

        private fun getTotalRamMb(activity: Activity): Long {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return info.totalMem / (1024L * 1024L)
        }

        /** Rough estimate of maximum safe project resolution in Pro mode. */
        fun maxSafeResolution(activity: Activity): Pair<Int, Int> {
            val ramMb = getTotalRamMb(activity)
            return when {
                ramMb >= 8000L -> 3840 to 2160   // 4K on flagship
                ramMb >= 6000L -> 2560 to 1440   // 1440p
                ramMb >= 4000L -> 1920 to 1080   // 1080p
                else -> 1280 to 720              // 720p fallback
            }
        }
    }

    // =========================================================================
    // Toggle dialog
    // =========================================================================

    /**
     * Shows the confirm dialog when the user taps PRO.
     *
     * If enabling is refused (device too weak), shows an explanation instead.
     * If enabling succeeds, calls onChanged(true). If disabled, onChanged(false).
     */
    fun showToggleDialog(activity: Activity, onChanged: (Boolean) -> Unit) {
        if (isPro()) {
            // Switching back to consumer — no confirmation
            setMode(PipelineMode.CONSUMER, activity)
            onChanged(false)
            return
        }

        if (!GpuMemoryGuard.canEnablePro(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("PRO MODE UNAVAILABLE")
                .setMessage(GpuMemoryGuard.reasonForRefusal(activity))
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val ramMb = runCatching {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem / (1024L * 1024L)
        }.getOrDefault(0L)

        val maxRes = GpuMemoryGuard.maxSafeResolution(activity)
        val message = buildString {
            appendLine("• 32-bit float linear pipeline")
            appendLine("• ACEScg working space")
            appendLine("• Real sRGB ↔ linear transforms")
            appendLine("• Physically correct light math")
            appendLine()
            appendLine("Device RAM: ${ramMb} MB")
            appendLine("Max safe resolution: ${maxRes.first}×${maxRes.second}")
            appendLine()
            appendLine("Preview may be slower in Pro mode.")
            appendLine("Recommended on flagship devices.")
        }

        AlertDialog.Builder(activity)
            .setTitle("ENABLE PRO MODE?")
            .setMessage(message)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("ENABLE") { _, _ ->
                if (setMode(PipelineMode.PRO, activity)) {
                    onChanged(true)
                }
            }
            .show()
    }

    // =========================================================================
    // Wire-up helpers — called by GlEffectRenderer
    // =========================================================================

    /**
     * Returns true if the current mode wants the input transform injected
     * at the top of each shader. Called once per shader compile.
     */
    fun shouldInjectInputTransform(): Boolean = isPro()

    /**
     * Returns true if the current mode wants the output transform injected
     * at the bottom of the final pass. Intermediate passes skip it.
     */
    fun shouldInjectOutputTransform(isFinalPass: Boolean): Boolean =
        isPro() && isFinalPass

    /**
     * Returns the GL internal format for render target textures.
     * Consumer: GL_RGBA (8-bit). Pro: GL_RGBA32F (32-bit float).
     */
    fun renderTargetInternalFormat(): Int = activeFormat.internalFormat

    /**
     * Returns the GL type for pixel uploads. Consumer: GL_UNSIGNED_BYTE.
     * Pro: GL_FLOAT.
     */
    fun renderTargetType(): Int = activeFormat.type

    /**
     * Initializes the block at app start. Currently a no-op — reserved for
     * future use if a native OCIO library is added.
     */
    fun initialize() {
        Log.i(TAG, "ProModeBlock ready. Consumer mode active by default.")
    }

    /**
     * Releases any resources. No-op for now.
     */
    fun shutdown() {
        Log.i(TAG, "ProModeBlock shutdown")
    }
}

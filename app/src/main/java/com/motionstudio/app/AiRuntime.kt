package com.motionstudio.app

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * AiRuntime — capability probe for AI / ML features.
 *
 * Never statically imports ML Kit or TFLite. Uses reflection so the app
 * compiles and runs with or without those Gradle dependencies. When a
 * capability is missing, `available` is false and `reason` explains why.
 *
 * When the dependency is present, `available` becomes true and the UI shows
 * the feature unlocked. The actual execution still lives in the file that
 * owns the feature (e.g. GlEffectRenderer for depth, TextBlock for captions).
 */
object AiRuntime {

    private const val TAG = "AiRuntime"

    /** A single AI feature the runtime knows about. */
    data class Capability(
        val id: String,
        val name: String,
        val available: Boolean,
        val reason: String,
        val requiresModel: Boolean = false,
        val modelAsset: String? = null,
    )

    // -------------------------------------------------------------------------
    // Dependency probes — cached after first call
    // -------------------------------------------------------------------------
    private var initialized = false
    private var context: Context? = null

    private val hasMlKitSegmentation: Boolean by lazy { classExists("com.google.mlkit.vision.segmentation.Segmenter") }
    private val hasMlKitObject: Boolean by lazy { classExists("com.google.mlkit.vision.objects.ObjectDetector") }
    private val hasMlKitLabels: Boolean by lazy { classExists("com.google.mlkit.vision.label.ImageLabeler") }
    private val hasMlKitFace: Boolean by lazy { classExists("com.google.mlkit.vision.face.FaceDetector") }
    private val hasMlKitText: Boolean by lazy { classExists("com.google.mlkit.vision.text.TextRecognizer") }
    private val hasTflite: Boolean by lazy { classExists("org.tensorflow.lite.Interpreter") }
    private val hasSpeechRecognizer: Boolean by lazy { classExists("android.speech.SpeechRecognizer") }

    private fun classExists(name: String): Boolean =
        try { Class.forName(name); true } catch (_: Throwable) { false }

    /** Assets the app ships with, checked lazily for the ones we care about. */
    private val bundledModels: Set<String> by lazy {
        val ctx = context ?: return@lazy emptySet()
        try {
            ctx.assets.list("models")?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    fun initialize(activity: Activity) {
        if (initialized) return
        context = activity.applicationContext
        initialized = true
        Log.i(TAG, "AiRuntime initialized. " + summary())
    }

    private fun summary(): String = buildString {
        append("MLKit(seg=").append(hasMlKitSegmentation)
        append(",obj=").append(hasMlKitObject)
        append(",lbl=").append(hasMlKitLabels)
        append(",face=").append(hasMlKitFace)
        append(",txt=").append(hasMlKitText)
        append(") TFLite=").append(hasTflite)
        append(" Models=").append(bundledModels.joinToString(","))
    }

    // -------------------------------------------------------------------------
    // Capability catalog — what MainActivity.buildAiTools() iterates
    // -------------------------------------------------------------------------
    fun capabilities(): List<Capability> = listOf(

        // ---------- Always available (no external deps) ----------
        Capability(
            id = "beats",
            name = "Beat Scan",
            available = true,
            reason = "",
        ),
        Capability(
            id = "autocut",
            name = "Auto Cut on Beats",
            available = true,
            reason = "",
        ),
        Capability(
            id = "captions",
            name = "Auto Captions",
            available = hasSpeechRecognizer,
            reason = if (hasSpeechRecognizer) ""
                     else "Speech recognition not available on this device",
        ),

        // ---------- ML Kit segmentation ----------
        Capability(
            id = "bgremove",
            name = "Background Remove",
            available = hasMlKitSegmentation,
            reason = if (hasMlKitSegmentation) ""
                     else "Add ML Kit segmentation-selfie to build.gradle",
        ),
        Capability(
            id = "subjectmask",
            name = "Subject Mask",
            available = hasMlKitSegmentation,
            reason = if (hasMlKitSegmentation) ""
                     else "Add ML Kit segmentation-selfie to build.gradle",
        ),
        Capability(
            id = "airotoscoping",
            name = "AI Roto Mask",
            available = hasMlKitSegmentation,
            reason = if (hasMlKitSegmentation) ""
                     else "Add ML Kit segmentation-selfie to build.gradle",
        ),

        // ---------- ML Kit object detection ----------
        Capability(
            id = "objecttrack",
            name = "Object Tracking",
            available = hasMlKitObject,
            reason = if (hasMlKitObject) ""
                     else "Add ML Kit object-detection to build.gradle",
        ),
        Capability(
            id = "planartrack",
            name = "Planar Tracking",
            available = false,
            reason = "Native solver required — not available on Android without OpenCV",
        ),

        // ---------- ML Kit image labeling ----------
        Capability(
            id = "scenes",
            name = "Scene Detection",
            available = hasMlKitLabels,
            reason = if (hasMlKitLabels) ""
                     else "Add ML Kit image-labeling to build.gradle",
        ),

        // ---------- ML Kit face ----------
        Capability(
            id = "facedetect",
            name = "Face Detection",
            available = hasMlKitFace,
            reason = if (hasMlKitFace) ""
                     else "Add ML Kit face-detection to build.gradle",
        ),

        // ---------- ML Kit text recognition ----------
        Capability(
            id = "ocr",
            name = "Text Recognition (OCR)",
            available = hasMlKitText,
            reason = if (hasMlKitText) ""
                     else "Add ML Kit text-recognition to build.gradle",
        ),

        // ---------- TFLite models ----------
        Capability(
            id = "depth",
            name = "Depth Map",
            available = hasTflite && bundledModels.contains("depth.tflite"),
            reason = when {
                !hasTflite -> "Add tensorflow-lite to build.gradle"
                !bundledModels.contains("depth.tflite") -> "Drop depth.tflite into assets/models/"
                else -> ""
            },
            requiresModel = true,
            modelAsset = "depth.tflite",
        ),
        Capability(
            id = "superres",
            name = "Super Resolution",
            available = hasTflite && bundledModels.contains("superres.tflite"),
            reason = when {
                !hasTflite -> "Add tensorflow-lite to build.gradle"
                !bundledModels.contains("superres.tflite") -> "Drop superres.tflite into assets/models/"
                else -> ""
            },
            requiresModel = true,
            modelAsset = "superres.tflite",
        ),
        Capability(
            id = "restore",
            name = "Restore / Denoise",
            available = hasTflite && bundledModels.contains("restore.tflite"),
            reason = when {
                !hasTflite -> "Add tensorflow-lite to build.gradle"
                !bundledModels.contains("restore.tflite") -> "Drop restore.tflite into assets/models/"
                else -> ""
            },
            requiresModel = true,
            modelAsset = "restore.tflite",
        ),
        Capability(
            id = "interp",
            name = "Frame Interpolation",
            available = false,
            reason = "RIFE model + native backend required — locked",
        ),

        // ---------- Always locked ----------
        Capability(
            id = "voiceclean",
            name = "Voice Cleanup",
            available = false,
            reason = "Requires RNNoise native library — not shipped",
        ),
        Capability(
            id = "silencedetect",
            name = "Silence Detect (ML)",
            available = false,
            reason = "VAD model required — locked",
        ),
        Capability(
            id = "smarttrim",
            name = "Smart Trim",
            available = false,
            reason = "Scene understanding model required — locked",
        ),
        Capability(
            id = "speedramp",
            name = "AI Speed Ramp",
            available = false,
            reason = "Requires optical flow solver — locked",
        ),
    )

    // -------------------------------------------------------------------------
    // Lookup helpers
    // -------------------------------------------------------------------------
    fun find(id: String): Capability? = capabilities().firstOrNull { it.id == id }

    fun isAvailable(id: String): Boolean = find(id)?.available == true

    /**
     * Verifies that all required permissions for AI features are granted.
     * Currently only RECORD_AUDIO matters (for Auto Captions via SpeechRecognizer).
     */
    fun hasAudioPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
               PackageManager.PERMISSION_GRANTED
    }

    /**
     * A compact human-readable status string for the top bar or a debug screen.
     */
    fun statusLine(): String {
        val caps = capabilities()
        val live = caps.count { it.available }
        return "AI: $live/${caps.size} available"
    }
}

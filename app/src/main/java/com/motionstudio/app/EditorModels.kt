package com.motionstudio.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// =============================================================================
// Enums — every distinct "kind" of thing in the project
// =============================================================================

enum class AssetKind { VIDEO, IMAGE, AUDIO, UNKNOWN }

enum class Interp {
    LINEAR, HOLD, EASE_IN, EASE_OUT, EASE_IN_OUT, BEZIER, BOUNCE, ELASTIC
}

enum class BlendMode {
    NORMAL, ADD, SCREEN, MULTIPLY, OVERLAY, SOFT_LIGHT, DIFFERENCE
}

enum class TrackKind { VIDEO, TEXT, AUDIO }

enum class MarkerKind { BEAT, SCENE, USER, CHAPTER }

enum class EditMode { INSERT, OVERWRITE }

enum class TimelineTool { SELECT, BLADE, HAND, ZOOM }

enum class DragMode {
    NONE,
    SCRUB,
    MOVE_CLIP,
    TRIM_IN,
    TRIM_OUT,
    SLIP,
    SLIDE,
    MARQUEE,
    PAN,
    LOOP_EDGE_START,
    LOOP_EDGE_END,
    TRACK_RESIZE,
    TRACK_REORDER,
}

// =============================================================================
// Media
// =============================================================================

data class MediaAsset(
    val id: Long,
    val uri: Uri,
    val name: String,
    val kind: AssetKind,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
)

// =============================================================================
// Keyframes + property tracks
// =============================================================================

data class Keyframe(
    var timeMs: Long,
    var value: Float,
    var interp: Interp = Interp.EASE_IN_OUT,
    var inHandleX: Float = 0.33f,
    var inHandleY: Float = 0f,
    var outHandleX: Float = 0.66f,
    var outHandleY: Float = 1f,
    var easeAmp: Float = 0.3f,
)

/**
 * A single animated channel (x, y, scale, rotation, opacity, etc.).
 * Holds a base value plus an optional list of keyframes. Sampling walks the
 * sorted keyframes and interpolates between the two that bracket `t` using
 * the interpolation mode stored on the *earlier* key.
 */
data class PropertyTrack(
    val channel: String,
    var baseValue: Float,
    val keys: MutableList<Keyframe> = mutableListOf(),
) {
    fun sample(t: Long): Float {
        if (keys.isEmpty()) return baseValue
        if (keys.size == 1) return keys[0].value
        val sorted = keys.sortedBy { it.timeMs }
        val a = sorted.lastOrNull { it.timeMs <= t } ?: return sorted.first().value
        val b = sorted.firstOrNull { it.timeMs >= t } ?: return sorted.last().value
        if (a.timeMs == b.timeMs) return a.value
        val p = ((t - a.timeMs).toFloat() / (b.timeMs - a.timeMs).toFloat()).coerceIn(0f, 1f)
        return when (a.interp) {
            Interp.HOLD -> a.value
            Interp.LINEAR -> Interpolation.lerp(a.value, b.value, p)
            Interp.EASE_IN -> Interpolation.lerp(a.value, b.value, p * p)
            Interp.EASE_OUT -> Interpolation.lerp(a.value, b.value, 1f - (1f - p) * (1f - p))
            Interp.EASE_IN_OUT -> Interpolation.lerp(a.value, b.value, p * p * (3f - 2f * p))
            Interp.BEZIER -> Interpolation.lerp(
                a.value, b.value,
                Interpolation.bezier(p, a.inHandleX, a.inHandleY, a.outHandleX, a.outHandleY),
            )
            Interp.BOUNCE -> Interpolation.lerp(a.value, b.value, Interpolation.bounceOut(p, a.easeAmp))
            Interp.ELASTIC -> Interpolation.lerp(a.value, b.value, Interpolation.elasticOut(p, a.easeAmp))
        }
    }
}

/**
 * Curve math shared by PropertyTrack, the 3D renderer, and text animation.
 * One source of truth for every interpolation mode in the app.
 */
object Interpolation {
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Solves a cubic bezier easing curve for input progress t in [0, 1]. */
    fun bezier(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        var lo = 0f
        var hi = 1f
        var u = t
        repeat(8) {
            val x = 3f * (1f - u) * (1f - u) * u * x1 +
                    3f * (1f - u) * u * u * x2 +
                    u * u * u
            if (x < t) lo = u else hi = u
            u = (lo + hi) / 2f
        }
        return 3f * (1f - u) * (1f - u) * u * y1 +
               3f * (1f - u) * u * u * y2 +
               u * u * u
    }

    fun bounceOut(t: Float, amp: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        var x = t
        val r = when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { x -= 1.5f / d1; n1 * x * x + 0.75f }
            x < 2.5f / d1 -> { x -= 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { x -= 2.625f / d1; n1 * x * x + 0.984375f }
        }
        return r * amp + (1f - amp) * t
    }

    fun elasticOut(t: Float, amp: Float): Float {
        if (t == 0f || t == 1f) return t
        val p = 0.3f
        return (2f.pow(-10f * t) * sin((t - p / 4f) * (2f * Math.PI.toFloat()) / p) + 1f) * amp +
               (1f - amp) * t
    }
}

// =============================================================================
// Effects
// =============================================================================

data class EffectInstance(
    val id: Long,
    val type: String,
    var enabled: Boolean = true,
    val params: MutableMap<String, Float> = mutableMapOf(
        "p0" to 1f, "p1" to 0f, "p2" to 0f,
    ),
    val paramTracks: MutableMap<String, PropertyTrack> = mutableMapOf(),
    var usesAi: Boolean = false,
    var aiModelKey: String? = null,
)

// =============================================================================
// Timeline items
// =============================================================================

data class Layer2D(
    val id: Long,
    val assetId: Long,
    var timelineStartMs: Long,
    var sourceInMs: Long,
    var sourceOutMs: Long,
    var trackIndex: Int = 0,
    var speed: Float = 1f,
    var volume: Float = 1f,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var linked: Boolean = true,
    var muted: Boolean = false,
    var parentId: Long? = null,
    var blendMode: BlendMode = BlendMode.NORMAL,
    val props: MutableMap<String, PropertyTrack> = mutableMapOf(
        "x" to PropertyTrack("x", 0f),
        "y" to PropertyTrack("y", 0f),
        "scale" to PropertyTrack("scale", 1f),
        "rotation" to PropertyTrack("rotation", 0f),
        "opacity" to PropertyTrack("opacity", 1f),
    ),
    val effects: MutableList<EffectInstance> = mutableListOf(),
) {
    fun sample(ch: String, t: Long): Float = props[ch]?.sample(t) ?: 0f

    fun timelineDurationMs(): Long =
        max(1L, ((sourceOutMs - sourceInMs) / speed).toLong())

    fun timelineEndMs(): Long = timelineStartMs + timelineDurationMs()
}

data class TextLayer(
    val id: Long,
    var text: String = "TEXT",
    var fontPath: String? = null,
    var fontSize: Float = 96f,
    var color: Int = Color.WHITE,
    var strokeColor: Int = Color.BLACK,
    var strokeWidth: Float = 0f,
    var shadow: Boolean = false,
    var shadowRadius: Float = 8f,
    var tracking: Float = 0f,
    var lineHeight: Float = 1.1f,
    var align: Int = 1,               // 0=left, 1=center, 2=right
    var preset: String = "NONE",
    var presetSpeed: Float = 1f,
    var timelineStartMs: Long = 0L,
    var timelineEndMs: Long = 5000L,
    var trackIndex: Int = 4,           // fixed text track
    val props: MutableMap<String, PropertyTrack> = mutableMapOf(
        "x" to PropertyTrack("x", 0f),
        "y" to PropertyTrack("y", 0f),
        "scale" to PropertyTrack("scale", 1f),
        "rotation" to PropertyTrack("rotation", 0f),
        "opacity" to PropertyTrack("opacity", 1f),
        "reveal" to PropertyTrack("reveal", 1f),
    ),
) {
    fun sample(ch: String, t: Long): Float = props[ch]?.sample(t) ?: 0f
}

data class OverlaySpec(
    val id: Long,
    var type: String,
    var text: String = "",
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    var radius: Float = 120f,
    var color: Int = Color.WHITE,
)

// =============================================================================
// Timeline decorations
// =============================================================================

data class BeatMarker(val timeMs: Long, val strength: Float)

data class UserMarker(
    val id: Long,
    val timeMs: Long,
    val kind: MarkerKind,
    var label: String = "",
    var color: Int = Color.WHITE,
)

data class TransitionPlacement(
    val id: Long,
    val leftClipId: Long,
    val rightClipId: Long,
    var transitionName: String,
    var durationMs: Long = 800L,
)

data class TrackState(
    var name: String,
    var kind: TrackKind,
    var muted: Boolean = false,
    var solo: Boolean = false,
    var locked: Boolean = false,
    var hidden: Boolean = false,
    var heightDp: Int = 48,
)

// =============================================================================
// Timeline data transfer objects
// =============================================================================

data class WaveformData(
    val peaks: FloatArray,
    val durationMs: Long,
    val samplesPerBucket: Int,
)

data class SelectionInfo(
    val clipCount: Int,
    val totalDurationMs: Long,
    val effectCount: Int,
)

data class SnapResult(
    val snapped: Boolean,
    val timeMs: Long,
    val source: String,
)

// =============================================================================
// 3D scene
// =============================================================================

data class Camera3D(
    var distance: Float = 6f,
    var yaw: Float = 35f,
    var pitch: Float = 18f,
    var panX: Float = 0f,
    var panY: Float = 0f,
    var fovY: Float = 45f,
)

data class Light3D(
    var dirX: Float = -0.45f,
    var dirY: Float = -0.75f,
    var dirZ: Float = -0.4f,
    var intensity: Float = 1f,
    var ambient: Float = 0.35f,
)

data class Layer3D(
    val id: Long,
    var title: String,
    var sourceAssetId: Long? = null,
    var color: Int = Color.rgb(120, 120, 120),
    var bitmap: Bitmap? = null,
    var textureId: Int = 0,
    var textureUploaded: Boolean = false,
) {
    val props: MutableMap<String, PropertyTrack> = mutableMapOf(
        "x" to PropertyTrack("x", 0f),
        "y" to PropertyTrack("y", 0f),
        "z" to PropertyTrack("z", 0f),
        "rotX" to PropertyTrack("rotX", 0f),
        "rotY" to PropertyTrack("rotY", 0f),
        "rotZ" to PropertyTrack("rotZ", 0f),
        "scaleX" to PropertyTrack("scaleX", 2f),
        "scaleY" to PropertyTrack("scaleY", 1.2f),
        "opacity" to PropertyTrack("opacity", 1f),
    )
    fun sample(ch: String, t: Long): Float = props[ch]?.sample(t) ?: 0f
}

// =============================================================================
// Fonts
// =============================================================================

data class FontAsset(
    val id: Long,
    val name: String,
    val path: String,
    val typeface: Typeface,
)

// =============================================================================
// Node graph
// =============================================================================

data class GNode(
    val id: Long,
    var title: String,
    var type: String,
    var x: Float,
    var y: Float,
    var w: Float = 180f,
    var h: Float = 110f,
    var thumb: Bitmap? = null,
    val inputs: MutableList<String> = mutableListOf("in"),
    val outputs: MutableList<String> = mutableListOf("out"),
    val params: MutableMap<String, Float> = mutableMapOf(),
)

data class GLink(
    val id: Long,
    val fromNode: Long,
    val fromPort: Int,
    val toNode: Long,
    val toPort: Int,
)

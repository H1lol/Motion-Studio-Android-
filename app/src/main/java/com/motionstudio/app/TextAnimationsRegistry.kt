package com.motionstudio.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * TextAnimationRegistry — 55 text animation presets.
 *
 * Each preset is a small function that, given a normalized progress value,
 * returns a CharTransform (offset, scale, rotation, opacity, reveal).
 *
 * The presets are used by TextAnimator when rasterizing a TextLayer to
 * a bitmap. For per-character presets (typewriter, wave, glitch), the
 * animator loops over each character and calls `compute` with a per-char
 * staggered t value.
 *
 * Modes:
 *   IN    — designed for entrance (t goes 0 → 1)
 *   OUT   — designed for exit (t goes 1 → 0)
 *   LOOP  — continuously animates while visible (t cycles 0 → 1 repeatedly)
 *   BOTH  — works for entrance or exit
 */
object TextAnimationRegistry {

    // =========================================================================
    // Data
    // =========================================================================

    data class CharTransform(
        val dx: Float = 0f,         // pixel offset X
        val dy: Float = 0f,         // pixel offset Y
        val scale: Float = 1f,      // uniform scale
        val scaleX: Float = 1f,     // horizontal scale (for flip effects)
        val scaleY: Float = 1f,     // vertical scale (for flip effects)
        val rotation: Float = 0f,   // degrees
        val opacity: Float = 1f,    // 0..1
        val reveal: Float = 1f,     // 0..1, for typewriter
        val blur: Float = 0f,       // blur radius in pixels
    ) {
        fun blend(other: CharTransform, amount: Float): CharTransform = CharTransform(
            dx = dx * (1f - amount) + other.dx * amount,
            dy = dy * (1f - amount) + other.dy * amount,
            scale = scale * (1f - amount) + other.scale * amount,
            scaleX = scaleX * (1f - amount) + other.scaleX * amount,
            scaleY = scaleY * (1f - amount) + other.scaleY * amount,
            rotation = rotation * (1f - amount) + other.rotation * amount,
            opacity = opacity * (1f - amount) + other.opacity * amount,
            reveal = reveal * (1f - amount) + other.reveal * amount,
            blur = blur * (1f - amount) + other.blur * amount,
        )
    }

    enum class PresetMode { IN, OUT, LOOP, BOTH }

    data class TextAnimationPreset(
        val name: String,
        val category: String,
        val mode: PresetMode,
        val durationMs: Long,
        val staggerMs: Long = 0L,
        val perCharacter: Boolean = false,
        val compute: (t: Float, charIndex: Int, charCount: Int) -> CharTransform,
    )

    // =========================================================================
    // Easing
    // =========================================================================

    object Ease {
        fun linear(t: Float) = t
        fun inQuad(t: Float) = t * t
        fun outQuad(t: Float) = 1f - (1f - t) * (1f - t)
        fun inOutQuad(t: Float) =
            if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
        fun outCubic(t: Float) = 1f - (1f - t).pow(3)
        fun outQuart(t: Float) = 1f - (1f - t).pow(4)
        fun outExpo(t: Float) = if (t >= 1f) 1f else 1f - 2f.pow(-10f * t)
        fun outBack(t: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
        }
        fun outElastic(t: Float): Float {
            if (t <= 0f) return 0f
            if (t >= 1f) return 1f
            val c4 = (2f * Math.PI / 3f).toFloat()
            return 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * c4) + 1f
        }
        fun outBounce(t: Float): Float {
            val n1 = 7.5625f
            val d1 = 2.75f
            return when {
                t < 1f / d1 -> n1 * t * t
                t < 2f / d1 -> { val x = t - 1.5f / d1; n1 * x * x + 0.75f }
                t < 2.5f / d1 -> { val x = t - 2.25f / d1; n1 * x * x + 0.9375f }
                else -> { val x = t - 2.625f / d1; n1 * x * x + 0.984375f }
            }
        }
    }

    // =========================================================================
    // FADE family (5)
    // =========================================================================

    private val fadeFamily = listOf(

        TextAnimationPreset(
            name = "Fade",
            category = "Fade",
            mode = PresetMode.BOTH,
            durationMs = 500L,
            compute = { t, _, _ -> CharTransform(opacity = t) },
        ),

        TextAnimationPreset(
            name = "Flash",
            category = "Fade",
            mode = PresetMode.BOTH,
            durationMs = 300L,
            compute = { t, _, _ ->
                val opacity = if (t < 0.5f) t * 2f else 1f
                CharTransform(opacity = opacity)
            },
        ),

        TextAnimationPreset(
            name = "Blink",
            category = "Fade",
            mode = PresetMode.LOOP,
            durationMs = 800L,
            compute = { t, _, _ ->
                val opacity = if (t < 0.5f) 1f else 0.2f
                CharTransform(opacity = opacity)
            },
        ),

        TextAnimationPreset(
            name = "Pulse",
            category = "Fade",
            mode = PresetMode.LOOP,
            durationMs = 1200L,
            compute = { t, _, _ ->
                val opacity = 0.65f + 0.35f * sin(t * 2f * Math.PI.toFloat())
                CharTransform(opacity = opacity.coerceIn(0f, 1f))
            },
        ),

        TextAnimationPreset(
            name = "Shimmer",
            category = "Fade",
            mode = PresetMode.LOOP,
            durationMs = 1500L,
            compute = { t, i, n ->
                val phase = (t + i.toFloat() / max(1, n)) * 2f * Math.PI.toFloat()
                val s = 1f + 0.05f * sin(phase)
                CharTransform(scale = s)
            },
        ),
    )

    // =========================================================================
    // SLIDE family (8)
    // =========================================================================

    private val slideFamily = listOf(

        TextAnimationPreset(
            name = "Slide Up",
            category = "Slide",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(dy = (1f - e) * 60f, opacity = e)
            },
        ),

        TextAnimationPreset(
            name = "Slide Down",
            category = "Slide",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(dy = -(1f - e) * 60f, opacity = e)
            },
        ),

        TextAnimationPreset(
            name = "Slide Left",
            category = "Slide",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(dx = (1f - e) * 80f, opacity = e)
            },
        ),

        TextAnimationPreset(
            name = "Slide Right",
            category = "Slide",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(dx = -(1f - e) * 80f, opacity = e)
            },
        ),

        TextAnimationPreset(
            name = "Slide Up Out",
            category = "Slide",
            mode = PresetMode.OUT,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.inQuad(t)
                CharTransform(dy = -e * 60f, opacity = 1f - e)
            },
        ),

        TextAnimationPreset(
            name = "Slide Down Out",
            category = "Slide",
            mode = PresetMode.OUT,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.inQuad(t)
                CharTransform(dy = e * 60f, opacity = 1f - e)
            },
        ),

        TextAnimationPreset(
            name = "Slide Diagonal",
            category = "Slide",
            mode = PresetMode.IN,
            durationMs = 600L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(
                    dx = (1f - e) * 60f,
                    dy = (1f - e) * 60f,
                    opacity = e,
                )
            },
        ),

        TextAnimationPreset(
            name = "Slide In From Side",
            category = "Slide",
            mode = PresetMode.IN,
            durationMs = 600L,
            perCharacter = true,
            staggerMs = 40L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(dx = (1f - e) * 120f, opacity = e)
            },
        ),
    )

    // =========================================================================
    // ZOOM family (6)
    // =========================================================================

    private val zoomFamily = listOf(

        TextAnimationPreset(
            name = "Zoom In",
            category = "Zoom",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(scale = e, opacity = e)
            },
        ),

        TextAnimationPreset(
            name = "Zoom Out",
            category = "Zoom",
            mode = PresetMode.OUT,
            durationMs = 400L,
            compute = { t, _, _ ->
                val e = Ease.inQuad(t)
                CharTransform(scale = 1f - e * 0.9f, opacity = 1f - e)
            },
        ),

        TextAnimationPreset(
            name = "Punch In",
            category = "Zoom",
            mode = PresetMode.IN,
            durationMs = 600L,
            compute = { t, _, _ ->
                val e = Ease.outBack(t)
                CharTransform(scale = e, opacity = min(1f, t * 2f))
            },
        ),

        TextAnimationPreset(
            name = "Shrink Out",
            category = "Zoom",
            mode = PresetMode.OUT,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.inQuad(t)
                CharTransform(scale = 1f - e * 0.5f, opacity = 1f - e)
            },
        ),

        TextAnimationPreset(
            name = "Pop",
            category = "Zoom",
            mode = PresetMode.BOTH,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outBack(t)
                CharTransform(scale = e, opacity = min(1f, t * 3f))
            },
        ),

        TextAnimationPreset(
            name = "Super Zoom",
            category = "Zoom",
            mode = PresetMode.IN,
            durationMs = 700L,
            compute = { t, _, _ ->
                val e = if (t < 0.7f) Ease.outExpo(t / 0.7f) * 1.15f
                        else 1.15f - 0.15f * ((t - 0.7f) / 0.3f)
                CharTransform(scale = e, opacity = min(1f, t * 2.5f))
            },
        ),
    )

    // =========================================================================
    // ROTATE family (6)
    // =========================================================================

    private val rotateFamily = listOf(

        TextAnimationPreset(
            name = "Spin In",
            category = "Rotate",
            mode = PresetMode.IN,
            durationMs = 600L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(
                    rotation = (1f - e) * 180f,
                    scale = e,
                    opacity = e,
                )
            },
        ),

        TextAnimationPreset(
            name = "Spin Out",
            category = "Rotate",
            mode = PresetMode.OUT,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.inQuad(t)
                CharTransform(
                    rotation = -e * 180f,
                    scale = 1f - e * 0.5f,
                    opacity = 1f - e,
                )
            },
        ),

        TextAnimationPreset(
            name = "Flip X",
            category = "Rotate",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(
                    scaleX = e,
                    opacity = min(1f, t * 2f),
                )
            },
        ),

        TextAnimationPreset(
            name = "Flip Y",
            category = "Rotate",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outCubic(t)
                CharTransform(
                    scaleY = e,
                    opacity = min(1f, t * 2f),
                )
            },
        ),

        TextAnimationPreset(
            name = "Tilt In",
            category = "Rotate",
            mode = PresetMode.IN,
            durationMs = 500L,
            compute = { t, _, _ ->
                val e = Ease.outBack(t)
                CharTransform(
                    rotation = (1f - e) * -15f,
                    opacity = min(1f, t * 2f),
                )
            },
        ),

        TextAnimationPreset(
            name = "Roll",
            category = "Rotate",
            mode = PresetMode.LOOP,
            durationMs = 4000L,
            compute = { t, _, _ ->
                CharTransform(rotation = t * 360f)
            },
        ),
    )

    // =========================================================================
    // BOUNCE family (5)
    // =========================================================================

    private val bounceFamily = listOf(

        TextAnimationPreset(
            name = "Bounce In",
            category = "Bounce",
            mode = PresetMode.IN,
            durationMs = 800L,
            compute = { t, _, _ ->
                val e = Ease.outBounce(t)
                CharTransform(dy = (1f - e) * -100f, opacity = min(1f, t * 3f))
            },
        ),

        TextAnimationPreset(
            name = "Bounce Out",
            category = "Bounce",
            mode = PresetMode.OUT,
            durationMs = 700L,
            compute = { t, _, _ ->
                val e = Ease.outBounce(t)
                CharTransform(dy = e * -100f, opacity = 1f - t)
            },
        ),

        TextAnimationPreset(
            name = "Elastic",
            category = "Bounce",
            mode = PresetMode.IN,
            durationMs = 1200L,
            compute = { t, _, _ ->
                val e = Ease.outElastic(t)
                CharTransform(scale = e, opacity = min(1f, t * 3f))
            },
        ),

        TextAnimationPreset(
            name = "Spring",
            category = "Bounce",
            mode = PresetMode.IN,
            durationMs = 900L,
            compute = { t, _, _ ->
                val e = Ease.outElastic(t)
                CharTransform(
                    scaleY = 0.5f + e * 0.5f,
                    scaleX = 1f / (0.5f + e * 0.5f),
                    opacity = min(1f, t * 3f),
                )
            },
        ),

        TextAnimationPreset(
            name = "Jello",
            category = "Bounce",
            mode = PresetMode.LOOP,
            durationMs = 1600L,
            compute = { t, _, _ ->
                val s = 1f + 0.08f * sin(t * 4f * Math.PI.toFloat())
                val sx = 1f / s
                CharTransform(scaleX = s, scaleY = sx)
            },
        ),
    )
// =========================================================================
// TYPE family (6) — per-character reveals
// =========================================================================

private val typeFamily = listOf(

    TextAnimationPreset(
        name = "Typewriter",
        category = "Type",
        mode = PresetMode.IN,
        durationMs = 1500L,
        perCharacter = true,
        staggerMs = 60L,
        compute = { t, _, _ ->
            CharTransform(reveal = if (t > 0f) 1f else 0f)
        },
    ),

    TextAnimationPreset(
        name = "Cursor",
        category = "Type",
        mode = PresetMode.LOOP,
        durationMs = 1000L,
        perCharacter = true,
        compute = { t, i, n ->
            // Blinking cursor on the last character
            if (i == n - 1) {
                CharTransform(opacity = if (t < 0.5f) 1f else 0f)
            } else {
                CharTransform()
            }
        },
    ),

    TextAnimationPreset(
        name = "Word By Word",
        category = "Type",
        mode = PresetMode.IN,
        durationMs = 1500L,
        perCharacter = true,
        staggerMs = 120L,
        compute = { t, _, _ ->
            CharTransform(reveal = t, opacity = t)
        },
    ),

    TextAnimationPreset(
        name = "Reverse Type",
        category = "Type",
        mode = PresetMode.OUT,
        durationMs = 1000L,
        perCharacter = true,
        staggerMs = 50L,
        compute = { t, _, _ ->
            CharTransform(reveal = 1f - t)
        },
    ),

    TextAnimationPreset(
        name = "Scramble",
        category = "Type",
        mode = PresetMode.IN,
        durationMs = 800L,
        perCharacter = true,
        staggerMs = 30L,
        compute = { t, i, _ ->
            val phase = t * 3f + i * 0.5f
            val jitter = sin(phase * 6f) * (1f - t) * 20f
            CharTransform(
                dx = jitter,
                opacity = if (t > 0.3f) 1f else t * 3f,
            )
        },
    ),

    TextAnimationPreset(
        name = "Decrypt",
        category = "Type",
        mode = PresetMode.IN,
        durationMs = 1000L,
        perCharacter = true,
        staggerMs = 40L,
        compute = { t, i, _ ->
            val charPhase = (t * 3f - i * 0.1f).coerceIn(0f, 1f)
            CharTransform(
                scaleX = 0.5f + charPhase * 0.5f,
                opacity = charPhase,
            )
        },
    ),
)

// =========================================================================
// WAVE family (5)
// =========================================================================

private val waveFamily = listOf(

    TextAnimationPreset(
        name = "Wave",
        category = "Wave",
        mode = PresetMode.LOOP,
        durationMs = 1400L,
        perCharacter = true,
        compute = { t, i, _ ->
            val phase = t * 2f * Math.PI.toFloat() + i * 0.5f
            CharTransform(dy = sin(phase) * 12f)
        },
    ),

    TextAnimationPreset(
        name = "Sine",
        category = "Wave",
        mode = PresetMode.LOOP,
        durationMs = 1600L,
        perCharacter = true,
        compute = { t, i, _ ->
            val phase = t * 2f * Math.PI.toFloat() + i * 0.3f
            CharTransform(dx = sin(phase) * 8f)
        },
    ),

    TextAnimationPreset(
        name = "Ripple",
        category = "Wave",
        mode = PresetMode.LOOP,
        durationMs = 1400L,
        perCharacter = true,
        compute = { t, i, n ->
            val center = (n - 1) / 2f
            val dist = abs(i - center) / max(1f, center)
            val phase = t * 2f * Math.PI.toFloat() - dist * 3f
            CharTransform(dy = sin(phase) * 15f)
        },
    ),

    TextAnimationPreset(
        name = "Cascade",
        category = "Wave",
        mode = PresetMode.IN,
        durationMs = 900L,
        perCharacter = true,
        staggerMs = 40L,
        compute = { t, _, _ ->
            val e = Ease.outCubic(t)
            CharTransform(dy = (1f - e) * -40f, opacity = e)
        },
    ),

    TextAnimationPreset(
        name = "Stagger",
        category = "Wave",
        mode = PresetMode.IN,
        durationMs = 1000L,
        perCharacter = true,
        staggerMs = 50L,
        compute = { t, _, _ ->
            val e = Ease.outBack(t)
            CharTransform(scale = e, opacity = min(1f, t * 3f))
        },
    ),
)

// =========================================================================
// GLITCH family (5)
// =========================================================================

private val glitchFamily = listOf(

    TextAnimationPreset(
        name = "RGB Split",
        category = "Glitch",
        mode = PresetMode.LOOP,
        durationMs = 600L,
        perCharacter = true,
        compute = { t, i, _ ->
            val jitter = if (sin(t * 20f + i * 3f) > 0.7f) 4f else 0f
            CharTransform(dx = jitter)
        },
    ),

    TextAnimationPreset(
        name = "Jitter",
        category = "Glitch",
        mode = PresetMode.LOOP,
        durationMs = 500L,
        perCharacter = true,
        compute = { t, i, _ ->
            val phase = (t * 30f + i * 5.3f).toInt()
            val r = (sin(phase.toFloat()) * 10000f).toInt() % 7 - 3
            CharTransform(dy = r.toFloat())
        },
    ),

    TextAnimationPreset(
        name = "Scramble",
        category = "Glitch",
        mode = PresetMode.IN,
        durationMs = 800L,
        perCharacter = true,
        staggerMs = 20L,
        compute = { t, i, _ ->
            val phase = (t * 20f + i).toInt()
            val r = (sin(phase.toFloat()) * 10000f).toInt() % 11 - 5
            val settled = t > 0.8f
            CharTransform(
                dx = if (settled) 0f else r.toFloat() * 3f,
                dy = if (settled) 0f else r.toFloat() * 2f,
                opacity = t,
            )
        },
    ),

    TextAnimationPreset(
        name = "Datamosh",
        category = "Glitch",
        mode = PresetMode.LOOP,
        durationMs = 900L,
        perCharacter = true,
        compute = { t, i, _ ->
            val block = (i / 3).toFloat()
            val shift = sin(t * 10f + block) * 8f
            CharTransform(dx = shift)
        },
    ),

    TextAnimationPreset(
        name = "Static",
        category = "Glitch",
        mode = PresetMode.LOOP,
        durationMs = 400L,
        perCharacter = true,
        compute = { t, i, _ ->
            val phase = (t * 20f + i * 2.1f).toInt()
            val r = (sin(phase.toFloat()) * 10000f).toInt() % 100
            val opacity = if (r < 15) 0.3f else 1f
            CharTransform(opacity = opacity)
        },
    ),
)

// =========================================================================
// POP family (4)
// =========================================================================

private val popFamily = listOf(

    TextAnimationPreset(
        name = "Pop In",
        category = "Pop",
        mode = PresetMode.IN,
        durationMs = 400L,
        perCharacter = true,
        staggerMs = 40L,
        compute = { t, _, _ ->
            val e = Ease.outBack(t)
            CharTransform(scale = e, opacity = min(1f, t * 3f))
        },
    ),

    TextAnimationPreset(
        name = "Pop Out",
        category = "Pop",
        mode = PresetMode.OUT,
        durationMs = 400L,
        perCharacter = true,
        staggerMs = 40L,
        compute = { t, _, _ ->
            val e = Ease.inQuad(t)
            CharTransform(scale = 1f - e, opacity = 1f - e)
        },
    ),

    TextAnimationPreset(
        name = "Scale Bounce",
        category = "Pop",
        mode = PresetMode.LOOP,
        durationMs = 1000L,
        compute = { t, _, _ ->
            val s = 1f + 0.1f * Ease.outBounce(if (t < 0.5f) t * 2f else (1f - t) * 2f)
            CharTransform(scale = s)
        },
    ),

    TextAnimationPreset(
        name = "Heart Beat",
        category = "Pop",
        mode = PresetMode.LOOP,
        durationMs = 1000L,
        compute = { t, _, _ ->
            val s = when {
                t < 0.15f -> 1f + t / 0.15f * 0.2f
                t < 0.3f -> 1.2f - (t - 0.15f) / 0.15f * 0.2f
                t < 0.45f -> 1f + (t - 0.3f) / 0.15f * 0.15f
                t < 0.6f -> 1.15f - (t - 0.45f) / 0.15f * 0.15f
                else -> 1f
            }
            CharTransform(scale = s)
        },
    ),
)

// =========================================================================
// SPECIAL family (5)
// =========================================================================

private val specialFamily = listOf(

    TextAnimationPreset(
        name = "Hand Write",
        category = "Special",
        mode = PresetMode.IN,
        durationMs = 2000L,
        compute = { t, _, _ ->
            CharTransform(reveal = t)
        },
    ),

    TextAnimationPreset(
        name = "Neon Flicker",
        category = "Special",
        mode = PresetMode.LOOP,
        durationMs = 1600L,
        compute = { t, _, _ ->
            val phase = (t * 8f).toInt()
            val r = (sin(phase.toFloat()) * 10000f).toInt() % 100
            val opacity = if (r < 20) 0.4f else 1f
            val blur = if (r < 20) 3f else 0f
            CharTransform(opacity = opacity, blur = blur)
        },
    ),

    TextAnimationPreset(
        name = "Fire",
        category = "Special",
        mode = PresetMode.LOOP,
        durationMs = 1200L,
        perCharacter = true,
        compute = { t, i, _ ->
            val phase = t * 2f * Math.PI.toFloat() + i * 0.4f
            val dy = -abs(sin(phase)) * 8f
            val scale = 1f + 0.05f * sin(phase * 2f)
            CharTransform(dy = dy, scale = scale)
        },
    ),

    TextAnimationPreset(
        name = "Sparkle",
        category = "Special",
        mode = PresetMode.LOOP,
        durationMs = 1400L,
        perCharacter = true,
        compute = { t, i, _ ->
            val phase = t * 2f * Math.PI.toFloat() + i * 0.7f
            val intensity = max(0f, sin(phase))
            CharTransform(
                scale = 1f + intensity * 0.15f,
                opacity = 0.7f + intensity * 0.3f,
            )
        },
    ),

    TextAnimationPreset(
        name = "Rainbow",
        category = "Special",
        mode = PresetMode.LOOP,
        durationMs = 2000L,
        perCharacter = true,
        compute = { t, i, n ->
            val phase = t * 2f * Math.PI.toFloat() + i.toFloat() / max(1, n) * 4f
            val brightness = 0.5f + 0.5f * sin(phase)
            CharTransform(opacity = 0.6f + brightness * 0.4f)
        },
    ),
)

// =========================================================================
// The full catalog
// =========================================================================

val ALL: List<TextAnimationPreset> =
    fadeFamily +
    slideFamily +
    zoomFamily +
    rotateFamily +
    bounceFamily +
    typeFamily +
    waveFamily +
    glitchFamily +
    popFamily +
    specialFamily

val COUNT: Int get() = ALL.size

val CATEGORIES: List<String> get() = ALL.map { it.category }.distinct()

fun byName(name: String): TextAnimationPreset? =
    ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }

fun byCategory(category: String): List<TextAnimationPreset> =
    ALL.filter { it.category.equals(category, ignoreCase = true) }

fun byMode(mode: PresetMode): List<TextAnimationPreset> =
    ALL.filter { it.mode == mode || it.mode == PresetMode.BOTH }

/**
 * Samples a preset at a given time inside a total duration.
 *
 * Handles the timing/phase calculation: entrance progress, hold, exit
 * progress, or loop progress. Returns a CharTransform the animator can
 * apply to the layer or a character.
 *
 * `charIndex` and `charCount` matter only for per-character presets —
 * for others, pass 0 and 1.
 */
fun sample(
    preset: TextAnimationPreset,
    localTimeMs: Long,
    totalDurationMs: Long,
    charIndex: Int = 0,
    charCount: Int = 1,
): CharTransform {
    val dur = max(1L, preset.durationMs)
    val t: Float = when (preset.mode) {
        PresetMode.IN -> {
            val span = min(dur, totalDurationMs)
            (localTimeMs.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        PresetMode.OUT -> {
            val startAt = totalDurationMs - dur
            if (localTimeMs < startAt) 0f
            else ((localTimeMs - startAt).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
        PresetMode.LOOP -> {
            val phase = (localTimeMs % dur).toFloat() / dur.toFloat()
            phase
        }
        PresetMode.BOTH -> {
            // Use IN for the first half, then hold
            val span = min(dur, totalDurationMs / 2L)
            (localTimeMs.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
    }

    // Apply stagger for per-character presets
    val adjusted: Float = if (preset.perCharacter && preset.staggerMs > 0L) {
        val charDelay = charIndex * preset.staggerMs
        val span = max(1L, preset.durationMs)
        ((localTimeMs - charDelay).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } else t

    return preset.compute(adjusted, charIndex, charCount)
}
    // =========================================================================
    // Convenience — common entrance + exit combinations
    // =========================================================================

    /**
     * A combined preset description used when a text layer needs both an
     * entrance and an exit. TextAnimator picks the right one based on where
     * the playhead is in the layer's duration.
     */
    data class Combined(
        val name: String,
        val inPreset: TextAnimationPreset,
        val outPreset: TextAnimationPreset?,
        val loopPreset: TextAnimationPreset? = null,
    )

    /**
     * Twenty common combos — one entry each for the most-used patterns in
     * CapCut and Alight Motion.
     */
    val COMBOS: List<Combined> by lazy {
        listOf(
            Combined("Fade In / Fade Out",
                byName("Fade")!!, byName("Fade")!!),
            Combined("Slide Up / Fade Out",
                byName("Slide Up")!!, byName("Fade")!!),
            Combined("Slide Down / Slide Down Out",
                byName("Slide Down")!!, byName("Slide Down Out")!!),
            Combined("Zoom In / Zoom Out",
                byName("Zoom In")!!, byName("Zoom Out")!!),
            Combined("Pop In / Pop Out",
                byName("Pop In")!!, byName("Pop Out")!!),
            Combined("Punch In / Shrink Out",
                byName("Punch In")!!, byName("Shrink Out")!!),
            Combined("Spin In / Spin Out",
                byName("Spin In")!!, byName("Spin Out")!!),
            Combined("Bounce In / Bounce Out",
                byName("Bounce In")!!, byName("Bounce Out")!!),
            Combined("Elastic / Shrink Out",
                byName("Elastic")!!, byName("Shrink Out")!!),
            Combined("Flip X / Fade Out",
                byName("Flip X")!!, byName("Fade")!!),
            Combined("Typewriter / Fade Out",
                byName("Typewriter")!!, byName("Fade")!!),
            Combined("Word By Word / Reverse Type",
                byName("Word By Word")!!, byName("Reverse Type")!!),
            Combined("Cascade / Slide Up Out",
                byName("Cascade")!!, byName("Slide Up Out")!!),
            Combined("Stagger / Fade Out",
                byName("Stagger")!!, byName("Fade")!!),
            Combined("Wave (Loop)",
                byName("Fade")!!, null, byName("Wave")!!),
            Combined("Pulse (Loop)",
                byName("Fade")!!, null, byName("Pulse")!!),
            Combined("Jello (Loop)",
                byName("Fade")!!, null, byName("Jello")!!),
            Combined("Shimmer (Loop)",
                byName("Fade")!!, null, byName("Shimmer")!!),
            Combined("Jitter (Loop)",
                byName("Fade")!!, null, byName("Jitter")!!),
            Combined("Fire (Loop)",
                byName("Fade")!!, null, byName("Fire")!!),
        )
    }

    fun comboByName(name: String): Combined? =
        COMBOS.firstOrNull { it.name.equals(name, ignoreCase = true) }

    // =========================================================================
    // Live preview helper — for the picker tile
    // =========================================================================

    /**
     * Returns a preview CharTransform for showing a mini animation in the
     * text preset picker. Same as sample(), but normalizes time to a
     * 2-second loop regardless of the preset's own duration.
     */
    fun previewSample(
        preset: TextAnimationPreset,
        previewProgress: Float,
        charIndex: Int = 0,
        charCount: Int = 1,
    ): CharTransform {
        val p = previewProgress.coerceIn(0f, 1f)
        return preset.compute(p, charIndex, charCount)
    }

    // =========================================================================
    // Diagnostics
    // =========================================================================

    data class RegistryStats(
        val total: Int,
        val byCategory: Map<String, Int>,
        val byMode: Map<PresetMode, Int>,
    )

    fun stats(): RegistryStats {
        val byCat = HashMap<String, Int>()
        val byMode = HashMap<PresetMode, Int>()
        for (p in ALL) {
            byCat[p.category] = (byCat[p.category] ?: 0) + 1
            byMode[p.mode] = (byMode[p.mode] ?: 0) + 1
        }
        return RegistryStats(ALL.size, byCat, byMode)
    }
}

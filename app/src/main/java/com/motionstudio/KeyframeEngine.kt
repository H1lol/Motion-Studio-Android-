package com.motionstudio.part1

import kotlin.math.*

enum class Interpolation { HOLD, LINEAR, BEZIER }

data class BezierHandle(val x: Double, val y: Double)

data class Keyframe(
    val id: KeyframeId = java.util.UUID.randomUUID().toString(),
    val timeUs: Long,
    val value: Double,
    val interpolation: Interpolation = Interpolation.BEZIER,
    val inHandle: BezierHandle = BezierHandle(0.25, 0.0),
    val outHandle: BezierHandle = BezierHandle(0.25, 1.0)
)

object KeyframeEngine {
    fun evaluate(keys: List<Keyframe>, timeUs: Long): Double {
        if (keys.isEmpty()) return 0.0
        val s = keys.sortedBy { it.timeUs }
        if (timeUs <= s.first().timeUs) return s.first().value
        if (timeUs >= s.last().timeUs) return s.last().value

        val r = s.indexOfFirst { it.timeUs >= timeUs }
        val a = s[r - 1]; val b = s[r]
        val t = (timeUs - a.timeUs).toDouble() / (b.timeUs - a.timeUs).toDouble()

        return when (a.interpolation) {
            Interpolation.HOLD -> a.value
            Interpolation.LINEAR -> lerp(a.value, b.value, t)
            Interpolation.BEZIER -> lerp(a.value, b.value, cubicBezier(t))
        }
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun cubicBezier(t0: Double): Double {
        fun x(t: Double) = 3*(1-t)*(1-t)*t*0.25 + 3*(1-t)*t*t*0.75 + t*t*t
        fun y(t: Double) = 3*(1-t)*(1-t)*t*0.0 + 3*(1-t)*t*t*1.0 + t*t*t
        var lo = 0.0; var hi = 1.0
        repeat(16) {
            val m = (lo + hi) / 2
            if (x(m) < t0) lo = m else hi = m
        }
        return y((lo + hi) / 2).coerceIn(0.0, 1.0)
    }
}

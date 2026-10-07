package com.motionstudio.part1

data class Vec2(val x: Float, val y: Float)
enum class MaskMode { ADD, SUBTRACT, INTERSECT, LIGHTEN, DARKEN }

data class MaskPath(
    val points: MutableList<Vec2> = mutableListOf(),
    var closed: Boolean = true,
    var featherPx: Float = 0f,
    var expansionPx: Float = 0f,
    var inverted: Boolean = false,
    var mode: MaskMode = MaskMode.ADD
)

data class TextStyle(
    var fontFamily: String = "sans-serif",
    var sizePx: Float = 64f,
    var trackingPx: Float = 0f,
    var lineSpacingPx: Float = 0f,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var fillAlpha: Float = 1f
)

data class TextLayerData(var text: String, var style: TextStyle = TextStyle())

data class ShapePath(
    val points: MutableList<Vec2> = mutableListOf(),
    var closed: Boolean = true
)

object Geometry {
    fun polygonArea(points: List<Vec2>): Double {
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(sum) * 0.5
    }

    fun bounds(points: List<Vec2>): FloatArray {
        if (points.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f)
        var minX = points[0].x; var minY = points[0].y
        var maxX = minX; var maxY = minY
        for (p in points) {
            minX = minOf(minX, p.x); minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y)
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}

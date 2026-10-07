package com.motionstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.motionstudio.part3.Alignment
import com.motionstudio.part3.TextEngine
import com.motionstudio.part3.TextLayerModel
import com.motionstudio.part3.TextRun
import com.motionstudio.part3.TextShadow
import kotlin.math.max

/**
 * Motion Studio — Android text renderer backed by the real Part 3 text model.
 *
 * Part 3 owns the text data model:
 *   TextRun
 *   TextShadow
 *   TextLayerModel
 *   Alignment
 *   TextEngine.flatten()
 *   TextEngine.advance()
 *
 * This adapter supplies the Android drawing implementation that the Part 3
 * cross-platform text model intentionally leaves to the platform layer.
 *
 * It supports:
 *   - multiple TextRun values in one layer
 *   - font family lookup through Android Typeface
 *   - size
 *   - tracking
 *   - kerning
 *   - fill
 *   - stroke
 *   - shadow
 *   - LEFT/CENTER/RIGHT/JUSTIFY alignment
 *
 * Rendering is performed on an Android Canvas; no fake text engine is created.
 */
class MotionStudioTextRenderer(
    private val context: Context
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    fun render(
        canvas: Canvas,
        layer: TextLayerModel,
        bounds: RectF
    ) {
        if (layer.runs.isEmpty()) return

        val flattened = TextEngine.flatten(layer)
        if (flattened.isEmpty()) return

        val totalWidth = layer.runs.sumOf { TextEngine.advance(it).toDouble() }.toFloat()
        val startX = when (layer.alignment) {
            Alignment.LEFT,
            Alignment.JUSTIFY -> bounds.left

            Alignment.CENTER -> bounds.centerX() - totalWidth / 2f

            Alignment.RIGHT -> bounds.right - totalWidth
        }

        var x = startX
        for (run in layer.runs) {
            x = drawRun(
                canvas = canvas,
                run = run,
                x = x,
                baseline = baselineFor(run, bounds),
                bounds = bounds
            )
        }
    }

    /**
     * Renders a layer into a new ARGB bitmap.
     *
     * The caller owns the returned bitmap and should recycle it when it is no
     * longer needed on API levels/workflows where explicit recycling is used.
     */
    fun renderToBitmap(
        layer: TextLayerModel,
        width: Int,
        height: Int
    ): Bitmap {
        require(width > 0 && height > 0) { "Bitmap dimensions must be positive." }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        render(
            canvas = canvas,
            layer = layer,
            bounds = RectF(
                0f,
                0f,
                width.toFloat(),
                height.toFloat()
            )
        )
        return bitmap
    }

    private fun drawRun(
        canvas: Canvas,
        run: TextRun,
        x: Float,
        baseline: Float,
        bounds: RectF
    ): Float {
        if (run.text.isEmpty()) return x

        val typeface = resolveTypeface(run.font)
        paint.reset()
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        paint.typeface = typeface
        paint.textSize = run.size
        paint.color = run.fill

        run.shadow?.let { shadow ->
            paint.setShadowLayer(
                shadow.blur,
                shadow.dx,
                shadow.dy,
                shadow.color
            )
        }

        if (run.strokeWidth > 0f && run.stroke != 0) {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = run.strokeWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = run.stroke
            drawTrackedText(canvas, run, x, baseline, fillAfterStroke = true)
            paint.clearShadowLayer()
            return x + TextEngine.advance(run)
        }

        paint.style = Paint.Style.FILL
        drawTrackedText(canvas, run, x, baseline, fillAfterStroke = false)
        paint.clearShadowLayer()
        return x + TextEngine.advance(run)
    }

    private fun drawTrackedText(
        canvas: Canvas,
        run: TextRun,
        startX: Float,
        baseline: Float,
        fillAfterStroke: Boolean
    ) {
        var x = startX

        run.text.forEachIndexed { index, character ->
            val glyph = character.toString()
            canvas.drawText(glyph, x, baseline, paint)

            val glyphAdvance = paint.measureText(glyph)
            val tracking = if (index < run.text.lastIndex) run.tracking else 0f
            val kerning = if (index < run.text.lastIndex) run.kerning else 0f
            x += glyphAdvance + tracking + kerning
        }

        if (fillAfterStroke && run.fill != 0) {
            paint.style = Paint.Style.FILL
            paint.color = run.fill
            paint.clearShadowLayer()

            x = startX
            run.text.forEachIndexed { index, character ->
                val glyph = character.toString()
                canvas.drawText(glyph, x, baseline, paint)
                x += paint.measureText(glyph)
                if (index < run.text.lastIndex) {
                    x += run.tracking + run.kerning
                }
            }
        }
    }

    private fun baselineFor(
        run: TextRun,
        bounds: RectF
    ): Float {
        val metrics = Paint.FontMetrics()
        paint.reset()
        paint.textSize = run.size
        paint.typeface = resolveTypeface(run.font)
        paint.getFontMetrics(metrics)

        // Center the text vertically inside the supplied render bounds.
        return bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
    }

    private fun resolveTypeface(fontName: String): Typeface {
        val requested = fontName.trim()
        if (requested.isEmpty() || requested.equals("sans-serif", true)) {
            return Typeface.create("sans-serif", Typeface.NORMAL)
        }

        // First try the Android/system family name. This keeps the Part 3
        // TextRun.font field useful without introducing another font schema.
        val system = Typeface.create(requested, Typeface.NORMAL)
        if (system != null) return system

        // Asset fonts can be referenced by their relative path, e.g.
        // "fonts/Inter-Regular.ttf". The app's font registry can later supply
        // exactly that asset name without changing TextRun.
        return try {
            Typeface.createFromAsset(context.assets, requested)
        } catch (_: RuntimeException) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
}

/**
 * Drop-in View for the Android UI/render layer.
 *
 * Call setLayer() whenever the evaluated Part 3 TextLayerModel changes.
 */
class MotionStudioTextView(
    context: Context
) : View(context) {

    private val renderer = MotionStudioTextRenderer(context)
    private var layer: TextLayerModel? = null
    private val renderBounds = RectF()

    fun setLayer(value: TextLayerModel?) {
        layer = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val current = layer ?: return
        renderBounds.set(
            0f,
            0f,
            width.toFloat(),
            height.toFloat()
        )

        renderer.render(
            canvas = canvas,
            layer = current,
            bounds = renderBounds
        )
    }
}

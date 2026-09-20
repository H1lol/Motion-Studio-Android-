package com.motionstudio.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * TransitionPicker — the transition browser.
 *
 * A full-screen overlay that shows every transition as a tile with a
 * runtime-rendered thumbnail. Tap a tile to pick that transition and
 * close the picker.
 *
 * Thumbnails are generated on a background thread using
 * TransitionBlock.ThumbnailRenderer (offscreen EGL). They're cached in
 * a static map so opening the picker a second time is instant.
 *
 * Usage from MainActivity:
 *   TransitionPicker.show(this) { index, def ->
 *       // apply def.name to the selected clips
 *   }
 */
object TransitionPicker {

    private const val TAG = "TransitionPicker"

    // -------------------------------------------------------------------------
    // Thumbnail cache — survives across picker opens
    // -------------------------------------------------------------------------
    private val thumbnailCache = HashMap<String, Bitmap>()
    private val cacheLock = Object()
    private var thumbWidth = 240
    private var thumbHeight = 135

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Shows the picker overlay. `onPicked` fires on the main thread with the
     * chosen transition's index and its TransitionDef.
     */
    fun show(
        activity: Activity,
        onPicked: (index: Int, def: TransitionRegistry.TransitionDef) -> Unit,
    ) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val overlay = buildOverlay(activity, onPicked)
        root.addView(overlay, ViewGroup.LayoutParams(-1, -1))

        // Start thumbnail generation on a background thread
        ensureThumbnails(activity, overlay) { updateTileImages(overlay) }
    }

    /** Clears the thumbnail cache. Call if you add transitions later. */
    fun clearCache() {
        synchronized(cacheLock) {
            thumbnailCache.values.forEach { it.recycle() }
            thumbnailCache.clear()
        }
    }

    // -------------------------------------------------------------------------
    // Overlay construction
    // -------------------------------------------------------------------------

    private fun buildOverlay(
        activity: Activity,
        onPicked: (index: Int, def: TransitionRegistry.TransitionDef) -> Unit,
    ): FrameLayout {
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(8, 8, 8))
            isClickable = true
        }

        // ---------- Header ----------
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(14, 14, 14))
            setPadding(dp(activity, 12), 0, dp(activity, 12), 0)
        }
        val title = TextView(activity).apply {
            text = "TRANSITIONS (${TransitionRegistry.COUNT})"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, -1, 1f))

        val closeBtn = Button(activity).apply {
            text = "CLOSE"
            setTextColor(Color.WHITE)
            textSize = 10f
            isAllCaps = false
            background = outlineBg(activity)
            setOnClickListener {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 32)))

        overlay.addView(header, FrameLayout.LayoutParams(-1, dp(activity, 48), Gravity.TOP))

        // ---------- Category chips ----------
        val chipsRow = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(12, 12, 12))
        }
        val chipContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6))
        }
        chipsRow.addView(chipContainer)

        val categories = listOf("ALL") + TransitionRegistry.CATEGORIES
        var selectedCategory = "ALL"
        val gridHolder = FrameLayout(activity)

        categories.forEach { cat ->
            val chip = Button(activity).apply {
                text = cat
                setTextColor(Color.WHITE)
                textSize = 9f
                isAllCaps = false
                background = outlineBg(activity)
                setPadding(dp(activity, 8), 0, dp(activity, 8), 0)
                setOnClickListener {
                    selectedCategory = cat
                    rebuildGrid(activity, gridHolder, selectedCategory, onPicked)
                }
            }
            chipContainer.addView(chip, LinearLayout.LayoutParams(-2, dp(activity, 30)).apply {
                marginEnd = dp(activity, 4)
            })
        }

        overlay.addView(
            chipsRow,
            FrameLayout.LayoutParams(-1, dp(activity, 46)).apply { topMargin = dp(activity, 48) },
        )

        // ---------- Grid ----------
        overlay.addView(
            gridHolder,
            FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = dp(activity, 94)
                bottomMargin = dp(activity, 8)
            },
        )

        // Initial build
        rebuildGrid(activity, gridHolder, "ALL", onPicked)

        return overlay
    }

    private fun rebuildGrid(
        activity: Activity,
        container: FrameLayout,
        category: String,
        onPicked: (Int, TransitionRegistry.TransitionDef) -> Unit,
    ) {
        container.removeAllViews()

        val list = if (category == "ALL") TransitionRegistry.ALL
                   else TransitionRegistry.byCategory(category)

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 12))
        }

        val columns = 3
        var row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        var col = 0

        list.forEachIndexed { _, def ->
            val tile = buildTile(activity, def, onPicked)
            val tileLp = LinearLayout.LayoutParams(0, dp(activity, 110), 1f).apply {
                marginEnd = dp(activity, 4)
                bottomMargin = dp(activity, 4)
            }
            row.addView(tile, tileLp)
            col++
            if (col == columns) {
                grid.addView(row, LinearLayout.LayoutParams(-1, -2))
                row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                col = 0
            }
        }
        if (col > 0) {
            while (col < columns) {
                val spacer = View(activity)
                row.addView(spacer, LinearLayout.LayoutParams(0, dp(activity, 110), 1f).apply {
                    marginEnd = dp(activity, 4)
                    bottomMargin = dp(activity, 4)
                })
                col++
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        scroll.addView(grid)
        container.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildTile(
        activity: Activity,
        def: TransitionRegistry.TransitionDef,
        onPicked: (Int, TransitionRegistry.TransitionDef) -> Unit,
    ): View {
        val wrapper = FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(20, 20, 20))
            setOnClickListener {
                val idx = TransitionRegistry.ALL.indexOf(def)
                onPicked(idx, def)
                (this.parent?.parent?.parent?.parent as? ViewGroup)?.let { root ->
                    // Climb up to the overlay and remove it
                }
                // Simpler: bubble up to find the overlay
                var v: View? = this
                while (v != null) {
                    if (v is FrameLayout && v.parent is ViewGroup &&
                        (v.parent as ViewGroup).id == android.R.id.content) break
                    if (v.parent is ViewGroup) {
                        val p = v.parent as ViewGroup
                        if (p.childCount > 0 && p.getChildAt(0) === v && p is FrameLayout
                            && p.background != null && p.layoutParams is FrameLayout.LayoutParams
                            && (p.layoutParams as FrameLayout.LayoutParams).width == -1
                            && (p.layoutParams as FrameLayout.LayoutParams).height == -1) {
                            // Found the overlay
                            (p.parent as? ViewGroup)?.removeView(p)
                            return@setOnClickListener
                        }
                    }
                    v = v.parent as? View
                }
            }
        }

        val img = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(30, 30, 40))
        }
        // Try to load from cache
        val cached = synchronized(cacheLock) { thumbnailCache[def.name] }
        if (cached != null) {
            img.setImageBitmap(cached)
        }
        img.tag = def.name

        wrapper.addView(img, FrameLayout.LayoutParams(-1, -1))

        val label = TextView(activity).apply {
            text = def.name
            setTextColor(Color.WHITE)
            textSize = 9f
            setBackgroundColor(0xB0000000.toInt())
            setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2))
            gravity = Gravity.CENTER
        }
        wrapper.addView(
            label,
            FrameLayout.LayoutParams(-1, dp(activity, 22), Gravity.BOTTOM),
        )

        return wrapper
    }

    // -------------------------------------------------------------------------
    // Thumbnail generation
    // -------------------------------------------------------------------------

    private fun ensureThumbnails(
        activity: Activity,
        overlay: FrameLayout,
        onDone: () -> Unit,
    ) {
        // If everything's already cached, we're done
        val missing = TransitionRegistry.ALL.filter {
            synchronized(cacheLock) { thumbnailCache[it.name] } == null
        }
        if (missing.isEmpty()) {
            onDone()
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        val ui = Handler(Looper.getMainLooper())

        executor.execute {
            val renderer = TransitionBlock.ThumbnailRenderer()
            val ok = renderer.initialize(thumbWidth, thumbHeight)
            if (!ok) {
                ui.post {
                    onDone()
                    Toast.makeText(activity, "Preview renderer unavailable", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val (fromBmp, toBmp) = TransitionBlock.generateSamplePair(thumbWidth, thumbHeight)

            var generated = 0
            for (def in missing) {
                try {
                    val bmp = renderer.renderTransition(
                        transitionName = def.name,
                        from = fromBmp,
                        to = toBmp,
                        progress = 0.5f,
                        width = thumbWidth,
                        height = thumbHeight,
                    )
                    if (bmp != null) {
                        synchronized(cacheLock) {
                            thumbnailCache[def.name] = bmp
                        }
                        generated++
                        // Update UI in batches
                        if (generated % 4 == 0) {
                            ui.post { updateTileImages(overlay) }
                        }
                    }
                } catch (t: Throwable) {
                    // Skip this one — leave tile blank
                }
            }

            fromBmp.recycle()
            toBmp.recycle()
            renderer.release()

            ui.post {
                updateTileImages(overlay)
                onDone()
            }
        }
    }

    private fun updateTileImages(root: View) {
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ImageView) {
                val name = child.tag as? String ?: continue
                val bmp = synchronized(cacheLock) { thumbnailCache[name] }
                if (bmp != null && child.drawable == null) {
                    child.setImageBitmap(bmp)
                }
            }
            if (child is ViewGroup) updateTileImages(child)
        }
    }

    // -------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------

    private fun dp(activity: Activity, v: Int): Int =
        (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun outlineBg(activity: Activity) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(
                dp(activity, 1),
                Color.rgb(120, 120, 120),
            )
            cornerRadius = dp(activity, 4).toFloat()
        }
}

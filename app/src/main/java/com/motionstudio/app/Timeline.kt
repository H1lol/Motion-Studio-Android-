package com.motionstudio.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * TimelineHost — the contract the timeline uses to read and mutate project state.
 * MainActivity implements this. The timeline never touches ProjectState directly.
 */
interface TimelineHost {

    // Read
    val projectLayers: List<Layer2D>
    val projectTextLayers: List<TextLayer>
    val projectAssets: List<MediaAsset>
    val projectBeatMarkers: List<BeatMarker>
    val projectUserMarkers: MutableList<UserMarker>
    val projectTransitions: MutableList<TransitionPlacement>
    val projectTrackStates: MutableList<TrackState>
    val projectFps: Int
    val projectWidth: Int
    val projectHeight: Int

    fun selectedClipIds(): Set<Long>
    fun playheadMs(): Long
    fun isPlaying(): Boolean
    fun isProMode(): Boolean

    // Write
    fun mutate(block: () -> Unit)
    fun setPlayhead(timeMs: Long)
    fun setSelection(ids: Set<Long>)
    fun seekPlaybackTo(timeMs: Long)

    // Clip ops
    fun requestSplitAt(timeMs: Long, clipId: Long)
    fun requestDelete(ids: Set<Long>, ripple: Boolean)
    fun requestMoveClip(id: Long, newStartMs: Long, newTrackIndex: Int)
    fun requestTrimClip(id: Long, newInMs: Long, newOutMs: Long, newStartMs: Long)
    fun requestAddTransition(leftId: Long, rightId: Long)
    fun requestAddMarker(timeMs: Long, kind: MarkerKind)

    // Media
    fun assetUri(assetId: Long): Uri?
    fun assetDurationMs(assetId: Long): Long

    // Misc
    fun toast(msg: String)
}

/**
 * TimelineView — the touch-first timeline.
 *
 * Owns: track headers, clips, waveforms, filmstrips, transitions, markers,
 * loop region, playhead, ruler, toolbar, HUD, context menu.
 *
 * Handles: scrub, move, trim, marquee, pan, loop resize, track resize, pinch zoom.
 */
class TimelineView(
    context: Context,
    private val host: TimelineHost,
) : View(context) {

    // -------------------------------------------------------------------------
    // Palette
    // -------------------------------------------------------------------------
    private val BLACK = Color.BLACK
    private val WHITE = Color.WHITE
    private val GRAY = Color.rgb(128, 128, 128)
    private val DARK = Color.rgb(20, 20, 20)
    private val HAIRLINE = Color.rgb(36, 36, 36)
    private val SELECTED = Color.rgb(240, 240, 240)
    private val SNAP_COLOR = Color.rgb(255, 200, 60)
    private val LOOP_COLOR = Color.rgb(255, 200, 60)
    private val MARKER_BEAT = Color.rgb(120, 200, 255)
    private val MARKER_SCENE = Color.rgb(255, 150, 80)
    private val MARKER_USER = Color.rgb(120, 255, 140)
    private val MARKER_CHAPTER = Color.rgb(200, 120, 255)

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------
    private val DP_HEADER_W = 92
    private val DP_RULER_H = 24
    private val DP_TOOLBAR_H = 32
    private val DP_HUD_H = 22
    private val DP_MIN_CLIP_W = 24
    private val DP_HANDLE_W = 12
    private val DP_TRANSITION_ICON = 20
    private val DP_MARKER_H = 8
    private val DP_TRACK_GAP = 2

    // -------------------------------------------------------------------------
    // Paints
    // -------------------------------------------------------------------------
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val pThumb = Paint(Paint.FILTER_BITMAP_FLAG)

    // -------------------------------------------------------------------------
    // View state
    // -------------------------------------------------------------------------
    private var zoom = 0.08f             // pixels per millisecond
    private var panX = 0f
    private var contentW = 1f

    private var tool: TimelineTool = TimelineTool.SELECT
    private var editMode: EditMode = EditMode.OVERWRITE
    private var snapEnabled = true
    private var dragMode: DragMode = DragMode.NONE

    private val selectedIds = mutableSetOf<Long>()
    private val marqueeRect = RectF()

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragClipId: Long = -1L
    private var dragOriginalStartMs: Long = 0
    private var dragOriginalTrack: Int = 0
    private var dragOffsetMs: Long = 0
    private var dragTrackResizeStartDp = 0
    private var dragTrackIndex = -1

    private var loopStartMs: Long = -1
    private var loopEndMs: Long = -1
    var loopEnabled: Boolean = false
        private set

    private var lastSnap: SnapResult? = null

    private var pinchBaseSpan = 0f
    private var pinchBaseZoom = 1f
    private var pinchFocusMs = 0L
    private var isPinching = false

    private val ioExecutor = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private val waveformCache = HashMap<Long, WaveformData>()
    private val filmstripCache = HashMap<Long, List<Bitmap>>()
    private val waveformPending = HashSet<Long>()
    private val filmstripPending = HashSet<Long>()
    private val missingMedia = HashSet<Long>()

    private var autoScrollEnabled = true

    private var contextMenuOpen = false
    private var contextMenuAnchor: RectF? = null
    private var contextMenuTargetId: Long = -1L

    private val buttonHitRects = mutableListOf<Pair<RectF, () -> Unit>>()

    // -------------------------------------------------------------------------
    // Gesture detectors
    // -------------------------------------------------------------------------
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val hit = hitTestClip(e.x, e.y) ?: return
                if (hit.id !in selectedIds) {
                    selectedIds.clear()
                    selectedIds.add(hit.id)
                    host.setSelection(selectedIds.toSet())
                }
                contextMenuTargetId = hit.id
                contextMenuAnchor = RectF(
                    e.x - dp(60f), e.y - dp(20f),
                    e.x + dp(60f), e.y + dp(140f)
                )
                contextMenuOpen = true
                invalidate()
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

    init {
        isFocusable = true
        isClickable = true
        setBackgroundColor(BLACK)
        selectedIds.addAll(host.selectedClipIds())
    }

    // =========================================================================
    // Public API
    // =========================================================================
    fun setTool(t: TimelineTool) { tool = t; invalidate() }
    fun currentTool(): TimelineTool = tool

    fun setEditMode(mode: EditMode) {
        editMode = mode
        host.toast("Edit mode: ${mode.name}")
        invalidate()
    }
    fun currentEditMode(): EditMode = editMode

    fun setSnapEnabled(enabled: Boolean) { snapEnabled = enabled; invalidate() }
    fun isSnapEnabled(): Boolean = snapEnabled

    fun zoomIn() = zoomAround(zoom * 1.25f, width / 2f)
    fun zoomOut() = zoomAround(zoom / 1.25f, width / 2f)

    fun zoomToFit() {
        val dur = max(1000L, projectDuration())
        val usableW = (width - dp(DP_HEADER_W)).toFloat()
        zoom = (usableW / dur.toFloat()).coerceIn(0.001f, 2f)
        panX = 0f
        invalidate()
    }

    fun zoomToSelection() {
        if (selectedIds.isEmpty()) return
        val items = allItems().filter { it.id in selectedIds }
        if (items.isEmpty()) return
        val minStart = items.minOf { it.startMs }
        val maxEnd = items.maxOf { it.endMs }
        val dur = max(500L, maxEnd - minStart)
        val usableW = (width - dp(DP_HEADER_W)).toFloat()
        zoom = (usableW / dur.toFloat()).coerceIn(0.001f, 2f)
        panX = max(0f, msToXContent(minStart) - dp(DP_HEADER_W).toFloat())
        invalidate()
    }

    fun scrollPlayheadIntoView() { autoScrollEnabled = true; invalidate() }
    fun setAutoScroll(enabled: Boolean) { autoScrollEnabled = enabled }

    fun setLoop(startMs: Long, endMs: Long) {
        loopStartMs = min(startMs, endMs)
        loopEndMs = max(startMs, endMs)
        loopEnabled = loopStartMs in 0 until loopEndMs
        invalidate()
    }

    fun clearLoop() {
        loopStartMs = -1
        loopEndMs = -1
        loopEnabled = false
        invalidate()
    }

    fun jumpToNextEdit() {
        val cur = host.playheadMs()
        val next = allItems().map { it.startMs }.filter { it > cur + 1 }.minOrNull()
            ?: projectDuration()
        host.setPlayhead(next)
        scrollPlayheadIntoView(); invalidate()
    }

    fun jumpToPrevEdit() {
        val cur = host.playheadMs()
        val prev = allItems().map { it.endMs }.filter { it < cur - 1 }.maxOrNull() ?: 0L
        host.setPlayhead(prev)
        scrollPlayheadIntoView(); invalidate()
    }

    fun jumpToNextMarker() {
        val cur = host.playheadMs()
        val next = (host.projectUserMarkers.map { it.timeMs } +
                    host.projectBeatMarkers.map { it.timeMs })
            .filter { it > cur + 1 }.minOrNull() ?: return
        host.setPlayhead(next); scrollPlayheadIntoView(); invalidate()
    }

    fun jumpToPrevMarker() {
        val cur = host.playheadMs()
        val prev = (host.projectUserMarkers.map { it.timeMs } +
                    host.projectBeatMarkers.map { it.timeMs })
            .filter { it < cur - 1 }.maxOrNull() ?: return
        host.setPlayhead(prev); scrollPlayheadIntoView(); invalidate()
    }

    fun addUserMarker(kind: MarkerKind = MarkerKind.USER) {
        host.requestAddMarker(host.playheadMs(), kind)
        invalidate()
    }

    fun selectionInfo(): SelectionInfo {
        if (selectedIds.isEmpty()) return SelectionInfo(0, 0L, 0)
        val items = allItems().filter { it.id in selectedIds }
        return SelectionInfo(
            items.size,
            items.sumOf { it.endMs - it.startMs },
            items.sumOf { it.effectCount },
        )
    }

    fun deleteSelection(ripple: Boolean) {
        if (selectedIds.isEmpty()) return
        host.requestDelete(selectedIds.toSet(), ripple)
        selectedIds.clear()
        host.setSelection(emptySet())
        invalidate()
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(allItems().map { it.id })
        host.setSelection(selectedIds.toSet())
        invalidate()
    }

    fun clearSelection() {
        selectedIds.clear()
        host.setSelection(emptySet())
        invalidate()
    }

    fun invalidateForProjectChange() {
        val valid = allItems().map { it.id }.toSet()
        selectedIds.retainAll(valid)
        host.setSelection(selectedIds.toSet())
        invalidate()
    }
    // =========================================================================
// Coordinate helpers
// =========================================================================
private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
private fun dp(v: Float): Float = v * resources.displayMetrics.density

private fun headerW(): Float = dp(DP_HEADER_W).toFloat()
private fun rulerH(): Float = dp(DP_RULER_H).toFloat()
private fun toolbarH(): Float = dp(DP_TOOLBAR_H).toFloat()
private fun hudH(): Float = dp(DP_HUD_H).toFloat()

private fun msToXContent(ms: Long): Float = headerW() + ms.toFloat() * zoom
private fun msToX(ms: Long): Float = msToXContent(ms) - panX

private fun xToMs(x: Float): Long {
    val rel = (x + panX) - headerW()
    return (rel / zoom).toLong().coerceAtLeast(0L)
}

private fun trackTopY(index: Int): Float {
    var y = rulerH() + toolbarH()
    for (i in 0 until index) y += dp(track(i).heightDp) + dp(DP_TRACK_GAP)
    return y
}

private fun trackBottomY(index: Int): Float =
    trackTopY(index) + dp(track(index).heightDp)

private fun track(index: Int): TrackState {
    while (host.projectTrackStates.size <= index) {
        val size = host.projectTrackStates.size
        host.projectTrackStates.add(
            TrackState(
                name = when {
                    size < 4 -> "V${size + 1}"
                    size == 4 -> "T1"
                    else -> "A${size - 4}"
                },
                kind = when {
                    size < 4 -> TrackKind.VIDEO
                    size == 4 -> TrackKind.TEXT
                    else -> TrackKind.AUDIO
                },
            )
        )
    }
    return host.projectTrackStates[index]
}

private fun trackCount(): Int = max(7, host.projectTrackStates.size)

private fun hudTopY(): Float = height - hudH()

private val contentBottomY: Float
    get() = trackTopY(trackCount())

private fun projectDuration(): Long {
    val l = host.projectLayers.maxOfOrNull { it.timelineEndMs() } ?: 0L
    val t = host.projectTextLayers.maxOfOrNull { it.timelineEndMs } ?: 0L
    return max(l, t)
}

// =========================================================================
// Timeline items — a flattened view of layers and text layers
// =========================================================================
private data class Item(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val trackIndex: Int,
    val kind: TrackKind,
    val isText: Boolean,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val speed: Float,
    val effectCount: Int,
    val assetId: Long,
    val visible: Boolean,
    val locked: Boolean,
    val muted: Boolean,
    val linked: Boolean,
)

private fun allItems(): List<Item> {
    val out = ArrayList<Item>(host.projectLayers.size + host.projectTextLayers.size)
    for (l in host.projectLayers) {
        out.add(
            Item(
                id = l.id,
                startMs = l.timelineStartMs,
                endMs = l.timelineEndMs(),
                trackIndex = l.trackIndex.coerceAtLeast(0),
                kind = kindForTrack(l.trackIndex),
                isText = false,
                sourceInMs = l.sourceInMs,
                sourceOutMs = l.sourceOutMs,
                speed = l.speed,
                effectCount = l.effects.size,
                assetId = l.assetId,
                visible = l.visible,
                locked = l.locked,
                muted = l.muted,
                linked = l.linked,
            )
        )
    }
    for (t in host.projectTextLayers) {
        out.add(
            Item(
                id = t.id,
                startMs = t.timelineStartMs,
                endMs = t.timelineEndMs,
                trackIndex = 4,
                kind = TrackKind.TEXT,
                isText = true,
                sourceInMs = 0,
                sourceOutMs = t.timelineEndMs - t.timelineStartMs,
                speed = 1f,
                effectCount = 0,
                assetId = -1L,
                visible = true,
                locked = false,
                muted = false,
                linked = true,
            )
        )
    }
    return out
}

private fun kindForTrack(idx: Int): TrackKind = when {
    idx < 4 -> TrackKind.VIDEO
    idx == 4 -> TrackKind.TEXT
    else -> TrackKind.AUDIO
}

private fun itemById(id: Long): Item? = allItems().firstOrNull { it.id == id }

// =========================================================================
// Measurement
// =========================================================================
override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = MeasureSpec.getSize(widthMeasureSpec)
    val h = MeasureSpec.getSize(heightMeasureSpec)
    val timelineDur = max(60_000L, projectDuration())
    contentW = headerW() + timelineDur * zoom + dp(200)
    setMeasuredDimension(w, h)
}

// =========================================================================
// onDraw
// =========================================================================
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    canvas.drawColor(BLACK)

    drawTracks(canvas)
    drawClips(canvas)
    drawTransitions(canvas)
    drawMarkers(canvas)
    drawLoopRegion(canvas)
    drawRuler(canvas)
    drawTrackHeaders(canvas)
    drawSnapLine(canvas)
    drawPlayhead(canvas)
    drawMarquee(canvas)
    drawToolbar(canvas)
    drawHud(canvas)
    if (contextMenuOpen) drawContextMenu(canvas)

    if (autoScrollEnabled && host.isPlaying()) {
        val px = msToX(host.playheadMs())
        if (px < headerW() + dp(40f) || px > width - dp(80f)) {
            panX = max(
                0f,
                msToXContent(host.playheadMs()) - width / 2f + headerW() / 2f
            )
        }
    }

    ensureLoadedAsync()
}

// -------------------------------------------------------------------------
private fun drawTracks(canvas: Canvas) {
    val top = toolbarH() + rulerH()
    val bottom = max(contentBottomY, hudTopY())
    canvas.save()
    canvas.clipRect(0f, top, width.toFloat(), bottom)
    for (i in 0 until trackCount()) {
        val t = track(i)
        val y0 = trackTopY(i)
        val y1 = y0 + dp(t.heightDp)
        pFill.color = if (i % 2 == 0) DARK else Color.rgb(26, 26, 26)
        canvas.drawRect(headerW(), y0, width.toFloat(), y1, pFill)
        if (t.hidden) {
            pFill.color = 0x40000000
            canvas.drawRect(headerW(), y0, width.toFloat(), y1, pFill)
        }
        pFill.color = HAIRLINE
        canvas.drawRect(headerW(), y1, width.toFloat(), y1 + dp(1), pFill)
    }
    canvas.restore()
}

private fun drawRuler(canvas: Canvas) {
    val yTop = toolbarH()
    val yBot = yTop + rulerH()
    pFill.color = Color.rgb(14, 14, 14)
    canvas.drawRect(0f, yTop, width.toFloat(), yBot, pFill)

    val unitMs = chooseRulerUnitMs()
    val startMs = max(0L, xToMs(headerW()))
    val endMs = xToMs(width.toFloat())
    var t = (startMs / unitMs) * unitMs
    pText.textSize = dp(8f)
    pStroke.color = HAIRLINE
    pStroke.strokeWidth = dp(1f)
    while (t <= endMs + unitMs) {
        val x = msToX(t)
        if (x >= headerW()) {
            canvas.drawLine(x, yTop, x, yBot, pStroke)
            pText.color = GRAY
            canvas.drawText(formatRulerLabel(t, unitMs), x + dp(3f), yTop + dp(12f), pText)
        }
        t += unitMs
    }
}

private fun chooseRulerUnitMs(): Long {
    val candidates = longArrayOf(
        100L, 250L, 500L, 1000L, 2000L, 5000L, 10_000L, 15_000L,
        30_000L, 60_000L, 120_000L, 300_000L, 600_000L, 900_000L,
    )
    for (c in candidates) if (c * zoom > dp(60f)) return c
    return 900_000L
}

private fun formatRulerLabel(ms: Long, unit: Long): String {
    val s = ms / 1000
    return if (unit < 1000L)
        String.format(Locale.US, "%d:%02d.%03d", s / 60, s % 60, ms % 1000)
    else
        String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

// -------------------------------------------------------------------------
private fun drawTrackHeaders(canvas: Canvas) {
    val top = toolbarH() + rulerH()
    val bottom = max(contentBottomY, hudTopY())
    canvas.save()
    canvas.clipRect(0f, top, headerW(), bottom)
    pFill.color = Color.rgb(10, 10, 10)
    canvas.drawRect(0f, top, headerW(), bottom, pFill)

    for (i in 0 until trackCount()) {
        val t = track(i)
        val y0 = trackTopY(i)
        val y1 = y0 + dp(t.heightDp)

        pText.color = WHITE
        pText.textSize = dp(10f)
        pText.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(t.name, dp(6f), y0 + dp(14f), pText)

        val btnY = y0 + dp(20f)
        val btnW = dp(20f)
        val btnH = dp(14f)
        val startX = dp(4f)
        val gap = dp(2f)
        drawToggle(canvas, startX, btnY, btnW, btnH, "M", t.muted, Color.rgb(255, 90, 90))
        drawToggle(canvas, startX + (btnW + gap), btnY, btnW, btnH, "S", t.solo, Color.rgb(255, 210, 80))
        drawToggle(canvas, startX + (btnW + gap) * 2, btnY, btnW, btnH, "L", t.locked, Color.rgb(120, 200, 255))
        drawToggle(canvas, startX + (btnW + gap) * 3, btnY, btnW, btnH, "H", t.hidden, Color.rgb(180, 180, 180))

        pFill.color = HAIRLINE
        canvas.drawRect(headerW() - dp(1f), y0, headerW(), y1, pFill)
    }
    canvas.restore()

    pFill.color = HAIRLINE
    canvas.drawRect(headerW() - dp(1f), top, headerW(), bottom, pFill)
}

private fun drawToggle(
    canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
    label: String, active: Boolean, activeColor: Int,
) {
    pFill.color = if (active) activeColor else Color.rgb(30, 30, 30)
    canvas.drawRoundRect(RectF(x, y, x + w, y + h), dp(2f), dp(2f), pFill)
    pStroke.color = if (active) activeColor else Color.rgb(90, 90, 90)
    pStroke.strokeWidth = dp(1f)
    canvas.drawRoundRect(RectF(x, y, x + w, y + h), dp(2f), dp(2f), pStroke)
    pText.color = if (active) BLACK else GRAY
    pText.textSize = dp(8f)
    pText.textAlign = Paint.Align.CENTER
    val b = y + h / 2f - (pText.descent() + pText.ascent()) / 2f
    canvas.drawText(label, x + w / 2f, b, pText)
    pText.textAlign = Paint.Align.LEFT
}

// -------------------------------------------------------------------------
private fun drawClips(canvas: Canvas) {
    val top = toolbarH() + rulerH()
    val bottom = max(contentBottomY, hudTopY())
    canvas.save()
    canvas.clipRect(headerW(), top, width.toFloat(), bottom)

    val items = allItems().sortedBy { it.id in selectedIds }
    for (item in items) {
        val ts = track(item.trackIndex)
        if (ts.hidden) continue
        drawClip(canvas, item, ts)
    }
    canvas.restore()
}

private fun drawClip(canvas: Canvas, item: Item, ts: TrackState) {
    val x0 = msToX(item.startMs)
    val x1 = msToX(item.endMs)
    val y0 = trackTopY(item.trackIndex) + dp(3f)
    val y1 = trackTopY(item.trackIndex) + dp(ts.heightDp) - dp(3f)
    if (x1 < headerW() || x0 > width) return

    val w = max(dp(DP_MIN_CLIP_W).toFloat(), x1 - x0)
    val selected = item.id in selectedIds

    val clipRect = RectF(x0, y0, x0 + w, y1)
    pFill.color = clipBaseColor(item, selected)
    canvas.drawRoundRect(clipRect, dp(3f), dp(3f), pFill)

    val innerRect = RectF(x0 + dp(1f), y0 + dp(1f), x0 + w - dp(1f), y1 - dp(1f))
    canvas.save()
    canvas.clipRect(innerRect)

    if (item.kind == TrackKind.AUDIO) {
        drawWaveform(canvas, item, innerRect)
    } else if (!item.isText) {
        drawFilmstrip(canvas, item, innerRect)
    }

    canvas.restore()

    pStroke.color = if (selected) SELECTED else Color.rgb(120, 120, 120)
    pStroke.strokeWidth = if (selected) dp(2f) else dp(1f)
    canvas.drawRoundRect(clipRect, dp(3f), dp(3f), pStroke)

    if (selected && w > dp(40f)) {
        pFill.color = SELECTED
        canvas.drawRect(x0, y0, x0 + dp(DP_HANDLE_W), y1, pFill)
        canvas.drawRect(x0 + w - dp(DP_HANDLE_W), y0, x0 + w, y1, pFill)
        pStroke.color = BLACK
        pStroke.strokeWidth = dp(1f)
        val midY = (y0 + y1) / 2f
        canvas.drawLine(x0 + dp(4f), midY - dp(4f), x0 + dp(4f), midY + dp(4f), pStroke)
        canvas.drawLine(x0 + dp(7f), midY - dp(4f), x0 + dp(7f), midY + dp(4f), pStroke)
        canvas.drawLine(x0 + w - dp(4f), midY - dp(4f), x0 + w - dp(4f), midY + dp(4f), pStroke)
        canvas.drawLine(x0 + w - dp(7f), midY - dp(4f), x0 + w - dp(7f), midY + dp(4f), pStroke)
    }

    pText.color = WHITE
    pText.textSize = dp(9f)
    pText.typeface = Typeface.DEFAULT_BOLD
    val name = if (item.isText) {
        "T: " + (host.projectTextLayers.firstOrNull { it.id == item.id }?.text?.take(14) ?: "")
    } else {
        host.projectAssets.firstOrNull { it.id == item.assetId }?.name?.take(20) ?: "CLIP"
    }
    canvas.save()
    canvas.clipRect(clipRect)
    canvas.drawText(name, x0 + dp(6f), y0 + dp(12f), pText)
    canvas.restore()

    var badgeX = x0 + dp(4f)
    val badgeY = y1 - dp(12f)
    val badgeH = dp(9f)
    if (abs(item.speed - 1f) > 0.001f) {
        badgeX = drawBadge(canvas, badgeX, badgeY, badgeH, "${formatSpeed(item.speed)}×",
            Color.rgb(255, 210, 80))
    }
    if (item.effectCount > 0) {
        badgeX = drawBadge(canvas, badgeX, badgeY, badgeH, "FX ${item.effectCount}",
            Color.rgb(120, 200, 255))
    }
    if (!item.isText && item.assetId in missingMedia) {
        pFill.color = Color.rgb(255, 80, 80)
        canvas.drawRect(x0, y0, x0 + w, y0 + dp(3f), pFill)
    }
    if (item.locked || !item.visible || item.muted) {
        val flag = buildString {
            if (item.locked) append("L")
            if (!item.visible) append("H")
            if (item.muted) append("M")
        }
        pText.color = Color.rgb(255, 240, 200)
        pText.textSize = dp(8f)
        canvas.drawText(flag, x0 + w - dp(20f), y1 - dp(4f), pText)
    }
}

private fun clipBaseColor(item: Item, selected: Boolean): Int {
    val base = when (item.kind) {
        TrackKind.VIDEO -> Color.rgb(30, 34, 44)
        TrackKind.TEXT -> Color.rgb(34, 28, 42)
        TrackKind.AUDIO -> Color.rgb(26, 38, 30)
    }
    return if (selected) lighten(base, 1.4f) else base
}

private fun lighten(color: Int, f: Float): Int {
    val r = min(255, (Color.red(color) * f).toInt())
    val g = min(255, (Color.green(color) * f).toInt())
    val b = min(255, (Color.blue(color) * f).toInt())
    return Color.rgb(r, g, b)
}

private fun drawBadge(
    canvas: Canvas, x: Float, y: Float, h: Float, label: String, color: Int,
): Float {
    pText.textSize = dp(8f)
    pText.typeface = Typeface.DEFAULT_BOLD
    val w = pText.measureText(label) + dp(6f)
    pFill.color = color
    canvas.drawRoundRect(RectF(x, y, x + w, y + h), dp(2f), dp(2f), pFill)
    pText.color = BLACK
    canvas.drawText(label, x + dp(3f), y + h - dp(2f), pText)
    return x + w + dp(3f)
}
private fun drawFilmstrip(canvas: Canvas, item: Item, rect: RectF) {
    val frames = filmstripCache[item.id] ?: return
    if (frames.isEmpty()) return
    val n = frames.size
    val fw = rect.width() / n
    for (i in 0 until n) {
        val fx0 = rect.left + i * fw
        val fx1 = fx0 + fw
        val bmp = frames[i]
        canvas.drawBitmap(
            bmp,
            Rect(0, 0, bmp.width, bmp.height),
            RectF(fx0, rect.top, fx1, rect.bottom),
            pThumb
        )
    }
}

private fun drawWaveform(canvas: Canvas, item: Item, rect: RectF) {
    val wf = waveformCache[item.assetId] ?: return
    if (wf.peaks.isEmpty()) return
    val dur = item.endMs - item.startMs
    if (dur <= 0) return
    val centerY = (rect.top + rect.bottom) / 2f
    val amp = rect.height() / 2f * 0.9f
    pFill.color = Color.rgb(120, 220, 160)
    val stepPx = dp(1f)
    var px = rect.left
    while (px < rect.right) {
        val tMs = item.startMs + ((px - rect.left) / rect.width() * dur).toLong()
        val srcMs = item.sourceInMs + ((tMs - item.startMs) * item.speed).toLong()
        val idx = ((srcMs.toFloat() / wf.durationMs) * wf.peaks.size).toInt()
        val peak = if (idx in wf.peaks.indices) wf.peaks[idx] else 0f
        val h = peak * amp
        canvas.drawRect(px, centerY - h, px + stepPx, centerY + h, pFill)
        px += stepPx
    }
}

private fun drawTransitions(canvas: Canvas) {
    val top = toolbarH() + rulerH()
    val bottom = max(contentBottomY, hudTopY())
    canvas.save()
    canvas.clipRect(headerW(), top, width.toFloat(), bottom)
    for (tr in host.projectTransitions) {
        val left = itemById(tr.leftClipId) ?: continue
        val x = msToX(left.endMs)
        if (x < headerW() || x > width) continue
        val y0 = trackTopY(left.trackIndex) + dp(3f)
        val sz = dp(DP_TRANSITION_ICON).toFloat()
        val cy = y0 + sz / 2f + dp(2f)
        val path = Path()
        path.moveTo(x, cy - sz / 2f)
        path.lineTo(x + sz / 2f, cy)
        path.lineTo(x, cy + sz / 2f)
        path.lineTo(x - sz / 2f, cy)
        path.close()
        pFill.color = Color.rgb(255, 200, 60)
        canvas.drawPath(path, pFill)
        pStroke.color = BLACK
        pStroke.strokeWidth = dp(1f)
        canvas.drawPath(path, pStroke)
        pText.color = BLACK
        pText.textSize = dp(9f)
        pText.typeface = Typeface.DEFAULT_BOLD
        pText.textAlign = Paint.Align.CENTER
        val b = cy - (pText.descent() + pText.ascent()) / 2f
        canvas.drawText("T", x, b, pText)
        pText.textAlign = Paint.Align.LEFT
    }
    canvas.restore()
}

private fun drawMarkers(canvas: Canvas) {
    val y0 = toolbarH() + rulerH() - dp(DP_MARKER_H)
    for (bm in host.projectBeatMarkers) {
        val x = msToX(bm.timeMs)
        if (x < headerW() || x > width) continue
        pFill.color = MARKER_BEAT
        val path = Path()
        path.moveTo(x, y0 + dp(DP_MARKER_H))
        path.lineTo(x + dp(4f), y0)
        path.lineTo(x - dp(4f), y0)
        path.close()
        canvas.drawPath(path, pFill)
    }
    for (bm in host.projectBeatMarkers) {
        if (bm.strength < 0.99f) continue
        val x = msToX(bm.timeMs)
        if (x < headerW() || x > width) continue
        pFill.color = MARKER_SCENE
        canvas.drawRect(x - dp(1.5f), y0, x + dp(1.5f), y0 + dp(DP_MARKER_H), pFill)
    }
    for (um in host.projectUserMarkers) {
        val x = msToX(um.timeMs)
        if (x < headerW() || x > width) continue
        val color = when (um.kind) {
            MarkerKind.USER -> MARKER_USER
            MarkerKind.CHAPTER -> MARKER_CHAPTER
            else -> um.color
        }
        pFill.color = color
        canvas.drawRect(x - dp(2f), y0, x + dp(2f), y0 + dp(DP_MARKER_H), pFill)
        if (um.label.isNotEmpty() && zoom > 0.05f) {
            pText.color = color
            pText.textSize = dp(8f)
            canvas.drawText(um.label, x + dp(4f), y0 + dp(DP_MARKER_H) - dp(1f), pText)
        }
    }
}

private fun drawLoopRegion(canvas: Canvas) {
    if (!loopEnabled || loopEndMs <= loopStartMs) return
    val x0 = msToX(loopStartMs)
    val x1 = msToX(loopEndMs)
    val top = toolbarH()
    val bot = max(contentBottomY, hudTopY())
    pFill.color = 0x22FFC83C
    canvas.drawRect(RectF(x0, top, x1, bot), pFill)
    pFill.color = LOOP_COLOR
    canvas.drawRect(x0, top, x0 + dp(2f), bot, pFill)
    canvas.drawRect(x1 - dp(2f), top, x1, bot, pFill)
}

private fun drawSnapLine(canvas: Canvas) {
    val snap = lastSnap ?: return
    if (!snap.snapped) return
    val x = msToX(snap.timeMs)
    pStroke.color = SNAP_COLOR
    pStroke.strokeWidth = dp(2f)
    canvas.drawLine(x, toolbarH(), x, hudTopY(), pStroke)
    pText.color = SNAP_COLOR
    pText.textSize = dp(8f)
    canvas.drawText(snap.source, x + dp(4f), toolbarH() + dp(10f), pText)
}

private fun drawPlayhead(canvas: Canvas) {
    val x = msToX(host.playheadMs())
    if (x < headerW() - dp(2f) || x > width + dp(2f)) return
    pFill.color = WHITE
    canvas.drawRect(x - dp(1f), toolbarH(), x + dp(1f), hudTopY(), pFill)
    val path = Path()
    path.moveTo(x - dp(7f), toolbarH() + dp(2f))
    path.lineTo(x + dp(7f), toolbarH() + dp(2f))
    path.lineTo(x, toolbarH() + rulerH() * 0.7f)
    path.close()
    canvas.drawPath(path, pFill)
}

private fun drawMarquee(canvas: Canvas) {
    if (dragMode != DragMode.MARQUEE) return
    pFill.color = 0x33FFFFFF
    canvas.drawRect(marqueeRect, pFill)
    pStroke.color = WHITE
    pStroke.strokeWidth = dp(1f)
    canvas.drawRect(marqueeRect, pStroke)
}

private fun drawToolbar(canvas: Canvas) {
    val h = toolbarH()
    pFill.color = Color.rgb(14, 14, 14)
    canvas.drawRect(0f, 0f, width.toFloat(), h, pFill)
    pFill.color = HAIRLINE
    canvas.drawRect(0f, h - dp(1f), width.toFloat(), h, pFill)

    buttonHitRects.clear()
    var x = dp(6f)
    val btnH = dp(22f)
    val btnY = (h - btnH) / 2f

    x = drawToolBtn(canvas, x, btnY, btnH, "SELECT", tool == TimelineTool.SELECT) {
        setTool(TimelineTool.SELECT)
    }
    x = drawToolBtn(canvas, x, btnY, btnH, "BLADE", tool == TimelineTool.BLADE) {
        setTool(TimelineTool.BLADE)
    }
    x = drawToolBtn(canvas, x, btnY, btnH, "HAND", tool == TimelineTool.HAND) {
        setTool(TimelineTool.HAND)
    }
    x = drawToolBtn(canvas, x, btnY, btnH, "ZOOM", tool == TimelineTool.ZOOM) {
        setTool(TimelineTool.ZOOM)
    }
    x += dp(6f)
    x = drawToolBtn(canvas, x, btnY, btnH, "SNAP", snapEnabled) {
        setSnapEnabled(!snapEnabled)
    }
    x = drawToolBtn(canvas, x, btnY, btnH, editMode.name,
        editMode == EditMode.INSERT) {
        setEditMode(if (editMode == EditMode.INSERT) EditMode.OVERWRITE else EditMode.INSERT)
    }

    val actions = listOf(
        "FIT" to { zoomToFit() },
        "+" to { zoomIn() },
        "−" to { zoomOut() },
        "|◀" to { jumpToPrevEdit() },
        "▶|" to { jumpToNextEdit() },
        "MARK" to { addUserMarker(MarkerKind.USER) },
        "LOOP" to {
            if (loopEnabled) clearLoop()
            else {
                val sel = allItems().filter { it.id in selectedIds }
                if (sel.isNotEmpty()) setLoop(sel.minOf { it.startMs }, sel.maxOf { it.endMs })
                else setLoop(host.playheadMs(), host.playheadMs() + 5000L)
            }
        },
        "DEL" to { deleteSelection(ripple = false) },
        "RIP" to { deleteSelection(ripple = true) },
    )
    var rx = width - dp(6f)
    for (i in actions.indices.reversed()) {
        val (label, _) = actions[i]
        val w = dp(50f)
        rx -= w + dp(3f)
        drawToolBtnAt(canvas, rx, btnY, w, btnH, label, false) { actions[i].second() }
    }
}

private fun drawToolBtn(
    canvas: Canvas, x: Float, y: Float, h: Float, label: String, active: Boolean,
    onClick: () -> Unit,
): Float {
    val w = dp(56f)
    drawToolBtnAt(canvas, x, y, w, h, label, active, onClick)
    return x + w + dp(3f)
}

private fun drawToolBtnAt(
    canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
    label: String, active: Boolean, onClick: () -> Unit,
) {
    val r = RectF(x, y, x + w, y + h)
    pFill.color = if (active) WHITE else Color.rgb(30, 30, 30)
    canvas.drawRoundRect(r, dp(3f), dp(3f), pFill)
    pStroke.color = if (active) WHITE else Color.rgb(80, 80, 80)
    pStroke.strokeWidth = dp(1f)
    canvas.drawRoundRect(r, dp(3f), dp(3f), pStroke)
    pText.color = if (active) BLACK else WHITE
    pText.textSize = dp(9f)
    pText.typeface = Typeface.DEFAULT_BOLD
    pText.textAlign = Paint.Align.CENTER
    val b = y + h / 2f - (pText.descent() + pText.ascent()) / 2f
    canvas.drawText(label, x + w / 2f, b, pText)
    pText.textAlign = Paint.Align.LEFT
    buttonHitRects.add(r to onClick)
}

private fun drawHud(canvas: Canvas) {
    val y0 = hudTopY()
    pFill.color = Color.rgb(10, 10, 10)
    canvas.drawRect(0f, y0, width.toFloat(), height.toFloat(), pFill)
    pFill.color = HAIRLINE
    canvas.drawRect(0f, y0, width.toFloat(), y0 + dp(1f), pFill)

    val dur = projectDuration()
    val ph = host.playheadMs()
    val info = selectionInfo()

    pText.color = WHITE
    pText.textSize = dp(10f)
    pText.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText(formatTimecode(ph), dp(8f), y0 + dp(15f), pText)

    pText.color = GRAY
    pText.textSize = dp(9f)
    pText.typeface = Typeface.DEFAULT
    canvas.drawText("DUR ${formatTimecode(dur)}", dp(120f), y0 + dp(15f), pText)

    if (info.clipCount > 0) {
        val s = "SEL ${info.clipCount}  •  ${formatTimecode(info.totalDurationMs)}  •  FX ${info.effectCount}"
        canvas.drawText(s, dp(260f), y0 + dp(15f), pText)
    }

    val right = "${host.projectFps} FPS  •  ${host.projectWidth}×${host.projectHeight}"
    val rw = pText.measureText(right)
    canvas.drawText(right, width - rw - dp(8f), y0 + dp(15f), pText)
}

private fun drawContextMenu(canvas: Canvas) {
    val r = contextMenuAnchor ?: return
    pFill.color = Color.rgb(24, 24, 24)
    canvas.drawRoundRect(r, dp(6f), dp(6f), pFill)
    pStroke.color = WHITE
    pStroke.strokeWidth = dp(1f)
    canvas.drawRoundRect(r, dp(6f), dp(6f), pStroke)

    val items = listOf(
        "Duplicate", "Split at playhead", "Delete", "Ripple delete",
        "Copy", "Paste", "Add transition", "Add marker", "Select all on track",
    )
    val itemH = dp(24f)
    var y = r.top + dp(6f)
    for (label in items) {
        pText.color = WHITE
        pText.textSize = dp(11f)
        pText.typeface = Typeface.DEFAULT
        canvas.drawText(label, r.left + dp(12f), y + dp(15f), pText)
        y += itemH
    }
}

private fun contextMenuItemRects(): List<Pair<RectF, String>> {
    val r = contextMenuAnchor ?: return emptyList()
    val items = listOf(
        "Duplicate", "Split at playhead", "Delete", "Ripple delete",
        "Copy", "Paste", "Add transition", "Add marker", "Select all on track",
    )
    val itemH = dp(24f)
    val out = ArrayList<Pair<RectF, String>>(items.size)
    var y = r.top + dp(6f)
    for (label in items) {
        out.add(RectF(r.left, y, r.right, y + itemH) to label)
        y += itemH
    }
    return out
}
    // =========================================================================
    // Touch
    // =========================================================================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPinching = false
                dragStartX = event.x
                dragStartY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                beginGesture(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    isPinching = true
                    dragMode = DragMode.NONE
                    val x0 = event.getX(0); val x1 = event.getX(1)
                    pinchBaseSpan = abs(x1 - x0).coerceAtLeast(1f)
                    pinchBaseZoom = zoom
                    pinchFocusMs = xToMs((x0 + x1) / 2f)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPinching && event.pointerCount >= 2) {
                    val x0 = event.getX(0); val x1 = event.getX(1)
                    val span = abs(x1 - x0).coerceAtLeast(1f)
                    val factor = span / pinchBaseSpan
                    zoomAround(pinchBaseZoom * factor, (x0 + x1) / 2f, anchorToMs = pinchFocusMs)
                    return true
                }
                updateGesture(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) isPinching = false
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isPinching) endGesture()
                isPinching = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun beginGesture(x: Float, y: Float) {
        if (contextMenuOpen) {
            for ((r, label) in contextMenuItemRects()) {
                if (r.contains(x, y)) {
                    handleContextAction(label)
                    contextMenuOpen = false
                    invalidate()
                    dragMode = DragMode.NONE
                    return
                }
            }
            contextMenuOpen = false
            invalidate()
        }

        if (y < toolbarH()) {
            for ((r, action) in buttonHitRects) {
                if (r.contains(x, y)) { action(); invalidate(); dragMode = DragMode.NONE; return }
            }
            return
        }

        if (x < headerW() && y > toolbarH() + rulerH()) {
            for (i in 0 until trackCount()) {
                val y0 = trackTopY(i)
                val y1 = y0 + dp(track(i).heightDp)
                if (y in y0..y1) {
                    val btnY = y0 + dp(20f)
                    val btnH = dp(14f)
                    if (y in btnY..(btnY + btnH)) {
                        val btnW = dp(20f); val gap = dp(2f); val startX = dp(4f)
                        val idx = ((x - startX) / (btnW + gap)).toInt()
                        val ts = track(i)
                        host.mutate {
                            when (idx) {
                                0 -> ts.muted = !ts.muted
                                1 -> ts.solo = !ts.solo
                                2 -> ts.locked = !ts.locked
                                3 -> ts.hidden = !ts.hidden
                            }
                        }
                        invalidate()
                        dragMode = DragMode.NONE
                        return
                    }
                    if (y > y1 - dp(6f)) {
                        dragMode = DragMode.TRACK_RESIZE
                        dragTrackIndex = i
                        dragTrackResizeStartDp = track(i).heightDp
                        return
                    }
                }
            }
            dragMode = DragMode.NONE
            return
        }

        if (y in toolbarH()..(toolbarH() + rulerH())) {
            dragMode = DragMode.SCRUB
            host.setPlayhead(xToMs(x))
            host.seekPlaybackTo(xToMs(x))
            invalidate()
            return
        }

        if (loopEnabled) {
            val lx0 = msToX(loopStartMs)
            val lx1 = msToX(loopEndMs)
            if (abs(x - lx0) < dp(6f)) { dragMode = DragMode.LOOP_EDGE_START; return }
            if (abs(x - lx1) < dp(6f)) { dragMode = DragMode.LOOP_EDGE_END; return }
        }

        val markerTop = toolbarH() + rulerH() - dp(DP_MARKER_H)
        val markerBottom = toolbarH() + rulerH()
        if (y in markerTop..markerBottom) {
            val clickMs = xToMs(x)
            val markers = host.projectUserMarkers.map { it.timeMs } +
                          host.projectBeatMarkers.map { it.timeMs }
            val hit = markers.minByOrNull { abs(it - clickMs) }
            if (hit != null && abs(hit - clickMs) < 500L) {
                host.setPlayhead(hit)
                host.seekPlaybackTo(hit)
                invalidate()
            }
            dragMode = DragMode.SCRUB
            return
        }

        val hit = hitTestClip(x, y)
        if (hit != null) {
            val ts = track(hit.trackIndex)
            if (ts.locked) {
                selectOnly(hit.id)
                dragMode = DragMode.NONE
                return
            }
            if (tool == TimelineTool.BLADE) {
                host.requestSplitAt(xToMs(x), hit.id)
                invalidate()
                dragMode = DragMode.NONE
                return
            }
            val x0 = msToX(hit.startMs)
            val x1 = msToX(hit.endMs)
            val nearLeft = x - x0 < dp(DP_HANDLE_W)
            val nearRight = x1 - x < dp(DP_HANDLE_W)
            dragClipId = hit.id
            dragOriginalStartMs = hit.startMs
            dragOriginalTrack = hit.trackIndex
            dragOffsetMs = xToMs(x) - hit.startMs
            dragMode = when {
                nearLeft -> DragMode.TRIM_IN
                nearRight -> DragMode.TRIM_OUT
                else -> DragMode.MOVE_CLIP
            }
            if (hit.id !in selectedIds) selectOnly(hit.id)
            invalidate()
            return
        }

        when (tool) {
            TimelineTool.HAND -> dragMode = DragMode.PAN
            else -> {
                dragMode = DragMode.MARQUEE
                marqueeRect.set(x, y, x, y)
                clearSelection()
            }
        }
        invalidate()
    }

    private fun updateGesture(x: Float, y: Float) {
        when (dragMode) {
            DragMode.SCRUB -> {
                val ms = xToMs(x).coerceAtLeast(0L)
                host.setPlayhead(ms)
                host.seekPlaybackTo(ms)
                invalidate()
            }
            DragMode.MOVE_CLIP -> {
                val item = itemById(dragClipId) ?: return
                val newStart = (xToMs(x) - dragOffsetMs).coerceAtLeast(0L)
                val snap = computeSnap(newStart, dragClipId, true)
                lastSnap = snap
                val target = if (snap.snapped) snap.timeMs else newStart
                val newTrack = trackIndexForY(y) ?: dragOriginalTrack
                val validTrack = track(newTrack).kind == kindForTrack(item.trackIndex)
                val finalTrack = if (validTrack) newTrack else dragOriginalTrack
                host.requestMoveClip(dragClipId, target, finalTrack)
                invalidate()
            }
            DragMode.TRIM_IN -> {
                val item = itemById(dragClipId) ?: return
                val newStart = xToMs(x).coerceAtLeast(0L)
                val snap = computeSnap(newStart, dragClipId, true)
                lastSnap = snap
                val target = if (snap.snapped) snap.timeMs else newStart
                if (target >= item.endMs - 50) return
                val delta = target - item.startMs
                val newIn = (item.sourceInMs + (delta * item.speed).toLong()).coerceAtLeast(0L)
                host.requestTrimClip(dragClipId, newIn, item.sourceOutMs, target)
                invalidate()
            }
            DragMode.TRIM_OUT -> {
                val item = itemById(dragClipId) ?: return
                val newEnd = xToMs(x).coerceAtLeast(item.startMs + 50)
                val snap = computeSnap(newEnd, dragClipId, true)
                lastSnap = snap
                val target = if (snap.snapped) snap.timeMs else newEnd
                val delta = target - item.endMs
                val newOut = (item.sourceOutMs + (delta * item.speed).toLong())
                    .coerceAtLeast(item.sourceInMs + 50)
                host.requestTrimClip(dragClipId, item.sourceInMs, newOut, item.startMs)
                invalidate()
            }
            DragMode.MARQUEE -> {
                marqueeRect.set(
                    min(dragStartX, x), min(dragStartY, y),
                    max(dragStartX, x), max(dragStartY, y),
                )
                selectedIds.clear()
                for (item in allItems()) {
                    val cx0 = msToX(item.startMs)
                    val cx1 = msToX(item.endMs)
                    val cy0 = trackTopY(item.trackIndex)
                    val cy1 = cy0 + dp(track(item.trackIndex).heightDp)
                    if (RectF.intersects(RectF(cx0, cy0, cx1, cy1), marqueeRect))
                        selectedIds.add(item.id)
                }
                host.setSelection(selectedIds.toSet())
                invalidate()
            }
            DragMode.PAN -> {
                val dx = x - lastTouchX
                panX = (panX - dx).coerceIn(0f, max(0f, contentW - width))
                invalidate()
            }
            DragMode.LOOP_EDGE_START -> {
                val ms = xToMs(x).coerceAtLeast(0L)
                loopStartMs = min(ms, loopEndMs - 100)
                loopEnabled = loopEndMs > loopStartMs
                invalidate()
            }
            DragMode.LOOP_EDGE_END -> {
                val ms = xToMs(x).coerceAtLeast(loopStartMs + 100)
                loopEndMs = ms
                loopEnabled = loopEndMs > loopStartMs
                invalidate()
            }
            DragMode.TRACK_RESIZE -> {
                if (dragTrackIndex < 0) return
                val deltaY = y - dragStartY
                val newDp = (dragTrackResizeStartDp +
                             deltaY / resources.displayMetrics.density).toInt()
                    .coerceIn(24, 200)
                track(dragTrackIndex).heightDp = newDp
                invalidate()
            }
            else -> Unit
        }
    }

    private fun endGesture() {
        lastSnap = null
        if (dragMode == DragMode.MOVE_CLIP ||
            dragMode == DragMode.TRIM_IN ||
            dragMode == DragMode.TRIM_OUT) {
            host.mutate { /* commit */ }
        }
        dragMode = DragMode.NONE
        dragClipId = -1L
        dragTrackIndex = -1
        invalidate()
    }

    private fun selectOnly(id: Long) {
        selectedIds.clear()
        selectedIds.add(id)
        host.setSelection(selectedIds.toSet())
    }

    private fun hitTestClip(x: Float, y: Float): Item? {
        if (x < headerW()) return null
        if (y < toolbarH() + rulerH() + dp(DP_MARKER_H)) return null
        for (i in 0 until trackCount()) {
            val y0 = trackTopY(i)
            val y1 = y0 + dp(track(i).heightDp)
            if (y !in y0..y1) continue
            val ms = xToMs(x)
            return allItems()
                .filter { it.trackIndex == i && ms in it.startMs until it.endMs }
                .maxByOrNull { it.id }
        }
        return null
    }

    private fun trackIndexForY(y: Float): Int? {
        for (i in 0 until trackCount()) {
            val y0 = trackTopY(i)
            val y1 = y0 + dp(track(i).heightDp)
            if (y in y0..y1) return i
        }
        return null
    }

    // =========================================================================
    // Snap
    // =========================================================================
    private fun computeSnap(proposed: Long, excludeId: Long, excludeSelf: Boolean): SnapResult {
        if (!snapEnabled) return SnapResult(false, proposed, "")
        val thresholdPx = dp(8f)
        val thresholdMs = (thresholdPx / zoom).toLong().coerceAtLeast(10L)

        var best = Long.MIN_VALUE
        var bestSource = ""
        var bestDist = Long.MAX_VALUE

        fun consider(c: Long, source: String) {
            val d = abs(c - proposed)
            if (d < thresholdMs && d < bestDist) {
                best = c; bestSource = source; bestDist = d
            }
        }

        consider(host.playheadMs(), "playhead")
        for (item in allItems()) {
            if (excludeSelf && item.id == excludeId) continue
            consider(item.startMs, "clip")
            consider(item.endMs, "clip")
        }
        for (bm in host.projectBeatMarkers) consider(bm.timeMs, "beat")
        for (um in host.projectUserMarkers) consider(um.timeMs, "marker")
        val grid = (proposed / 1000L) * 1000L
        consider(grid, "grid")
        consider(grid + 1000L, "grid")
        if (loopEnabled) { consider(loopStartMs, "loop"); consider(loopEndMs, "loop") }

        return if (best == Long.MIN_VALUE) SnapResult(false, proposed, "")
        else SnapResult(true, best, bestSource)
    }

    // =========================================================================
    // Zoom
    // =========================================================================
    private fun zoomAround(newZoom: Float, focusXView: Float, anchorToMs: Long = -1L) {
        val clamped = newZoom.coerceIn(0.002f, 2f)
        val focusMs = if (anchorToMs >= 0) anchorToMs else xToMs(focusXView)
        zoom = clamped
        panX = (msToXContent(focusMs) - focusXView).coerceAtLeast(0f)
        invalidate()
    }

    // =========================================================================
    // Async loaders
    // =========================================================================
    private fun ensureLoadedAsync() {
        for (item in allItems()) {
            if (item.isText) continue
            if (item.kind == TrackKind.AUDIO) {
                if (item.assetId !in waveformCache && item.assetId !in waveformPending) {
                    waveformPending.add(item.assetId)
                    loadWaveformAsync(item.assetId)
                }
            }
            if (item.kind == TrackKind.VIDEO &&
                item.id !in filmstripCache && item.id !in filmstripPending) {
                filmstripPending.add(item.id)
                loadFilmstripAsync(item)
            }
        }
    }

    private fun loadWaveformAsync(assetId: Long) {
        val uri = host.assetUri(assetId) ?: run {
            missingMedia.add(assetId); waveformPending.remove(assetId); return
        }
        ioExecutor.execute {
            try {
                val wf = extractWaveform(uri)
                ui.post {
                    waveformCache[assetId] = wf
                    waveformPending.remove(assetId)
                    invalidate()
                }
            } catch (_: Throwable) {
                ui.post { waveformPending.remove(assetId) }
            }
        }
    }

    private fun loadFilmstripAsync(item: Item) {
        val uri = host.assetUri(item.assetId) ?: run {
            missingMedia.add(item.assetId); filmstripPending.remove(item.id); return
        }
        ioExecutor.execute {
            try {
                val frames = extractFilmstrip(uri, item.sourceInMs, item.sourceOutMs, 8)
                ui.post {
                    filmstripCache[item.id] = frames
                    filmstripPending.remove(item.id)
                    invalidate()
                }
            } catch (_: Throwable) {
                ui.post { filmstripPending.remove(item.id) }
            }
        }
    }

    private fun extractWaveform(uri: Uri): WaveformData {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val idx = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalStateException("no audio track")
        extractor.selectTrack(idx)
        val fmt = extractor.getTrackFormat(idx)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(fmt, null, null, 0)
        decoder.start()

        val framesPerBucket = max(1, sampleRate / 100)
        val peaks = ArrayList<Float>(1024)
        var bucketSamples = 0
        var bucketMax = 0f
        var inputDone = false
        var outputDone = false
        val info = MediaCodec.BufferInfo()

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val buf = decoder.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        var i = 0
                        while (i + 1 < bytes.size) {
                            val lo = bytes[i].toInt() and 0xff
                            val hi = bytes[i + 1].toInt()
                            val s = ((hi shl 8) or lo).toShort().toInt() / 32768f
                            val a = abs(s)
                            if (a > bucketMax) bucketMax = a
                            bucketSamples++
                            i += 2 * channels
                            if (bucketSamples >= framesPerBucket) {
                                peaks.add(bucketMax)
                                bucketSamples = 0
                                bucketMax = 0f
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                        outputDone = true
                }
            }
        } finally {
            try { decoder.stop() } catch (_: Throwable) {}
            decoder.release()
            extractor.release()
        }

        return WaveformData(peaks.toFloatArray(), host.assetDurationMs(-1L), framesPerBucket)
    }

    private fun extractFilmstrip(uri: Uri, inMs: Long, outMs: Long,count: Int): List<Bitmap> {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val out = ArrayList<Bitmap>(count)
            for (i in 0 until count) {
                val t = inMs + (outMs - inMs) * i / max(1, count - 1)
                val bmp = mmr.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val targetH = (dp(28f) * resources.displayMetrics.density).toInt().coerceAtLeast(32)
                    val targetW = (targetH * 16 / 9).coerceAtLeast(32)
                    val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                    if (scaled !== bmp) bmp.recycle()
                    out.add(scaled)
                }
            }
            return out
        } finally {
            try { mmr.release() } catch (_: Throwable) {}
        }
    }

    // =========================================================================
    // Context menu actions
    // =========================================================================
    private fun handleContextAction(label: String) {
        val id = contextMenuTargetId
        when (label) {
            "Duplicate" -> host.toast("Duplicate: implement in host")
            "Split at playhead" -> host.requestSplitAt(host.playheadMs(), id)
            "Delete" -> host.requestDelete(setOf(id), ripple = false)
            "Ripple delete" -> host.requestDelete(setOf(id), ripple = true)
            "Copy" -> host.toast("Copied")
            "Paste" -> host.toast("Pasted")
            "Add transition" -> {
                val item = itemById(id) ?: return
                val next = allItems()
                    .filter { it.trackIndex == item.trackIndex && it.id != id &&
                              it.startMs >= item.endMs - 1 }
                    .minByOrNull { it.startMs } ?: return
                host.requestAddTransition(item.id, next.id)
            }
            "Add marker" -> host.requestAddMarker(host.playheadMs(), MarkerKind.USER)
            "Select all on track" -> {
                val item = itemById(id) ?: return
                selectedIds.clear()
                for (i in allItems()) if (i.trackIndex == item.trackIndex) selectedIds.add(i.id)
                host.setSelection(selectedIds.toSet())
            }
        }
        invalidate()
    }

    // =========================================================================
    // Utilities
    // =========================================================================
    private fun formatTimecode(ms: Long): String {
        val fps = host.projectFps
        val totalFrames = (ms * fps / 1000.0).toLong()
        val f = (totalFrames % fps).toInt()
        val ts = totalFrames / fps
        val s = ts % 60
        val m = (ts / 60) % 60
        val h = ts / 3600
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", h, m, s, f)
    }

    private fun formatSpeed(s: Float): String =
        if (abs(s - s.toInt()) < 0.01f) s.toInt().toString()
        else String.format(Locale.US, "%.1f", s)
}

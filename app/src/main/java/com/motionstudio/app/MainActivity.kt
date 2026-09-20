package com.motionstudio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

/**
 * MainActivity — CapCut-style portrait layout.
 *
 * Five rows top to bottom:
 *   1. Top bar         (title, status, export)
 *   2. Preview         (GL viewer, takes remaining space)
 *   3. Transport       (undo, prev, play, next, redo)
 *   4. Timeline        (tracks, ~150dp)
 *   5. Bottom tabs     (MEDIA / EDIT / FX / TEXT / AI / AUDIO / COLOR / DELIVER)
 *
 * Tapping a bottom tab opens a sheet that slides up from the bottom.
 * Tapping outside the sheet closes it.
 */
class MainActivity : Activity(), TimelineHost {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    private val BLACK = Color.BLACK
    private val WHITE = Color.WHITE
    private val GRAY = Color.rgb(128, 128, 128)
    private val DARK_SURFACE = Color.rgb(14, 14, 14)

    private val REQ_MEDIA = 1001
    private val REQ_PROJECT_OPEN = 1004
    private val REQ_PROJECT_SAVE = 1005
    private val REQ_FONT_IMPORT = 1006

    private val executor = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // Project state
    // -------------------------------------------------------------------------
    private val state = ProjectState()

    private var pipelineMode: ProModeBlock.PipelineMode = ProModeBlock.PipelineMode.CONSUMER
    private var currentPage: String = "EDIT"
    private var selectedClipIds: MutableSet<Long> = mutableSetOf()
    private var selectedAssetId: Long? = null

    private var glView: android.opengl.GLSurfaceView? = null
    private var renderer: GlEffectRenderer? = null

    private val fontManager: FontManager by lazy { FontManager(this) }
    private val serializer: ProjectSerializer by lazy { ProjectSerializer(this) }

    // -------------------------------------------------------------------------
    // UI refs
    // -------------------------------------------------------------------------
    private lateinit var root: FrameLayout
    private lateinit var statusLabel: TextView
    private lateinit var viewerFrame: FrameLayout
    private lateinit var timelineView: TimelineView
    private lateinit var timelineScroll: HorizontalScrollView
    private lateinit var timelinePanel: LinearLayout
    private lateinit var toolPanel: LinearLayout
    private lateinit var sheetOverlay: FrameLayout
    private lateinit var sheetContent: LinearLayout
    private lateinit var sheetTitle: TextView
    private lateinit var nodeOverlay: FrameLayout
    private lateinit var scene3DOverlay: FrameLayout
    // =========================================================================
// Lifecycle
// =========================================================================
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    ProModeBlock.initialize()
    AiRuntime.initialize(this)

    buildUi()
    goImmersive()

    state.addListener(object : ProjectState.Listener {
        override fun onProjectChanged() {
            refreshInspector()
            if (::timelineView.isInitialized) timelineView.invalidateForProjectChange()
        }
    })

    state.codec = object : ProjectState.SnapshotCodec {
        override fun encode(state: ProjectState) = serializer.serialize(state)
        override fun decode(json: String, into: ProjectState) =
            serializer.deserialize(json, into)
    }
}

override fun onResume() {
    super.onResume()
    goImmersive()
}

override fun onPause() {
    super.onPause()
}

override fun onDestroy() {
    renderer?.release()
    executor.shutdownNow()
    super.onDestroy()
}

override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    goImmersive()
}

private fun goImmersive() {
    if (Build.VERSION.SDK_INT >= 30) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
}
// =========================================================================
// UI construction
// =========================================================================
private fun buildUi() {
    root = FrameLayout(this).apply { setBackgroundColor(BLACK) }
    setContentView(root)

    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BLACK)
    }
    root.addView(column, FrameLayout.LayoutParams(-1, -1))

    // Row 1 — top bar
    column.addView(buildTopBar(), LinearLayout.LayoutParams(-1, dp(46)))

    // Row 2 — preview (flexible)
    viewerFrame = FrameLayout(this).apply { setBackgroundColor(BLACK) }
    column.addView(viewerFrame, LinearLayout.LayoutParams(-1, 0, 1f))
    buildPreview()

    // Row 3 — transport
    column.addView(buildTransport(), LinearLayout.LayoutParams(-1, dp(46)))

    // Row 4 — timeline
    timelinePanel = buildTimeline()
    column.addView(timelinePanel, LinearLayout.LayoutParams(-1, dp(150)))

    // Row 5 — bottom tabs
    column.addView(buildBottomTabs(), LinearLayout.LayoutParams(-1, dp(58)))

    // Sheet overlay (hidden until a tab is tapped)
    sheetOverlay = FrameLayout(this).apply {
        setBackgroundColor(0xCC000000.toInt())
        visibility = View.GONE
        isClickable = true
        setOnClickListener { closeSheet() }
    }
    root.addView(sheetOverlay, FrameLayout.LayoutParams(-1, -1))

    sheetContent = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(DARK_SURFACE)
        isClickable = true
    }
    sheetOverlay.addView(
        sheetContent,
        FrameLayout.LayoutParams(-1, dp(380), Gravity.BOTTOM),
    )

    buildNodeOverlay()
    build3DOverlay()

    switchPage("EDIT")
}

// -------------------------------------------------------------------------
// Row 1 — top bar
// -------------------------------------------------------------------------
private fun buildTopBar(): LinearLayout {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(BLACK)
        setPadding(dp(12), 0, dp(8), 0)
    }
    bar.addView(textLabel("MOTION STUDIO", 12f).apply {
        typeface = Typeface.DEFAULT_BOLD
    }, LinearLayout.LayoutParams(0, -1, 1f))

    statusLabel = textLabel("READY", 9f).apply {
        setTextColor(GRAY)
        gravity = Gravity.CENTER
    }
    bar.addView(statusLabel, LinearLayout.LayoutParams(dp(56), -1))

    bar.addView(outlineButton("EXPORT") { renderCurrent() },
        LinearLayout.LayoutParams(dp(74), dp(32)))

    return bar
}

// -------------------------------------------------------------------------
// Row 2 — preview
// -------------------------------------------------------------------------
private fun buildPreview() {
    val r = GlEffectRenderer(this, state)
    renderer = r

    glView = android.opengl.GLSurfaceView(this).apply {
        setEGLContextClientVersion(2)
        setRenderer(r)
        renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
    viewerFrame.addView(glView, FrameLayout.LayoutParams(-1, -1))

    // Empty-state hint
    val hint = textLabel("Tap MEDIA to import", 11f).apply {
        setTextColor(GRAY)
        gravity = Gravity.CENTER
    }
    viewerFrame.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

    // Tiny FIT / 1:1 in the top-right
    val hud = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setPadding(dp(6), dp(4), dp(6), dp(4))
    }
    hud.addView(outlineButton("FIT") { fitViewer() },
        LinearLayout.LayoutParams(dp(44), dp(26)))
    hud.addView(outlineButton("1:1") { resetViewer() },
        LinearLayout.LayoutParams(dp(44), dp(26)).apply { marginStart = dp(4) })
    viewerFrame.addView(hud,
        FrameLayout.LayoutParams(-2, dp(32), Gravity.TOP or Gravity.END))
}

// -------------------------------------------------------------------------
// Row 3 — transport
// -------------------------------------------------------------------------
private fun buildTransport(): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(BLACK)
        setPadding(dp(6), 0, dp(6), 0)
    }
    row.addView(outlineButton("UNDO") { undo() },
        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })
    row.addView(outlineButton("|<") { timelineView.jumpToPrevEdit() },
        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })
    row.addView(outlineButton("PLAY") { togglePlayback() },
        LinearLayout.LayoutParams(0, dp(34), 2f).apply { marginEnd = dp(3) })
    row.addView(outlineButton(">|") { timelineView.jumpToNextEdit() },
        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })
    row.addView(outlineButton("REDO") { redo() },
        LinearLayout.LayoutParams(0, dp(34), 1f))
    return row
}
// -------------------------------------------------------------------------
// Row 4 — timeline
// -------------------------------------------------------------------------
private fun buildTimeline(): LinearLayout {
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BLACK)
    }

    timelineScroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
    }
    timelineView = TimelineView(this, this)
    timelineScroll.addView(timelineView,
        HorizontalScrollView.LayoutParams(-2, -1))
    panel.addView(timelineScroll, LinearLayout.LayoutParams(-1, 0, 1f))

    return panel
}

// -------------------------------------------------------------------------
// Row 5 — bottom tabs
// -------------------------------------------------------------------------
private fun buildBottomTabs(): LinearLayout {
    val scroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(BLACK)
    }
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }
    val tabs = listOf(
        "MEDIA", "EDIT", "FX", "TEXT", "AI", "AUDIO", "COLOR", "3D"
    )
    tabs.forEach { name ->
        row.addView(outlineButton(name) { openSheet(name) },
            LinearLayout.LayoutParams(dp(72), dp(42)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
    }
    scroll.addView(row)
    val wrapper = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BLACK)
    }
    wrapper.addView(scroll, LinearLayout.LayoutParams(-1, -1))
    return wrapper
}

// -------------------------------------------------------------------------
// Node / 3D overlays
// -------------------------------------------------------------------------
private fun buildNodeOverlay() {
    nodeOverlay = FrameLayout(this).apply {
        setBackgroundColor(BLACK)
        visibility = View.GONE
    }
    root.addView(nodeOverlay, FrameLayout.LayoutParams(-1, -1))

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }
    header.addView(
        textLabel("NODE GRAPH", 11f).apply { typeface = Typeface.DEFAULT_BOLD },
        LinearLayout.LayoutParams(0, -1, 1f)
    )
    header.addView(outlineButton("CLOSE") { closeNodes() },
        LinearLayout.LayoutParams(dp(64), dp(32)))
    nodeOverlay.addView(header, FrameLayout.LayoutParams(-1, dp(44)))

    val placeholder = View(this).apply { setBackgroundColor(BLACK) }
    nodeOverlay.addView(placeholder,
        FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(44) })
}

private fun build3DOverlay() {
    scene3DOverlay = FrameLayout(this).apply {
        setBackgroundColor(BLACK)
        visibility = View.GONE
    }
    root.addView(scene3DOverlay, FrameLayout.LayoutParams(-1, -1))

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }
    header.addView(
        textLabel("3D SCENE", 11f).apply { typeface = Typeface.DEFAULT_BOLD },
        LinearLayout.LayoutParams(0, -1, 1f)
    )
    header.addView(outlineButton("CLOSE") { close3D() },
        LinearLayout.LayoutParams(dp(64), dp(32)))
    scene3DOverlay.addView(header, FrameLayout.LayoutParams(-1, dp(44)))

    val placeholder = View(this).apply { setBackgroundColor(BLACK) }
    scene3DOverlay.addView(placeholder,
        FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(44) })
}

// -------------------------------------------------------------------------
// Sheet — the sliding panel from the bottom
// -------------------------------------------------------------------------
private fun openSheet(name: String) {
    sheetContent.removeAllViews()

    // Header row
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.rgb(24, 24, 24))
        setPadding(dp(14), 0, dp(10), 0)
    }
    sheetTitle = textLabel(name, 12f).apply {
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(sheetTitle, LinearLayout.LayoutParams(0, -1, 1f))
    header.addView(outlineButton("CLOSE") { closeSheet() },
        LinearLayout.LayoutParams(dp(64), dp(30)))
    sheetContent.addView(header, LinearLayout.LayoutParams(-1, dp(46)))

    // Scrolling body
    toolPanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(14))
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(DARK_SURFACE)
    }
    scroll.addView(toolPanel)
    sheetContent.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

    // Fill the panel with the right content
    when (name) {
        "MEDIA" -> buildMediaTools()
        "EDIT" -> buildEditTools()
        "FX" -> buildFxTools()
        "AI" -> buildAiTools()
        "AUDIO" -> buildAudioTools()
        "COLOR" -> buildColorTools()
        "TEXT" -> buildTextTools()
        "3D" -> build3DTools()
        "DELIVER" -> buildDeliverTools()
    }

    sheetOverlay.visibility = View.VISIBLE
}

private fun closeSheet() {
    sheetOverlay.visibility = View.GONE
}

// -------------------------------------------------------------------------
// Page switching
// -------------------------------------------------------------------------
private fun switchPage(page: String) {
    currentPage = page
    closeNodes()
    close3D()
    when (page) {
        "EDIT" -> selectTool("EDIT")
        "TEXT" -> selectTool("TEXT")
        "FX" -> selectTool("FX")
        "NODES" -> openNodes()
        "3D" -> open3D()
        "DELIVER" -> selectTool("DELIVER")
    }
}

private fun selectTool(tool: String) {
    openSheet(tool)
}
// =========================================================================
// Tool panels — one per bottom tab
// =========================================================================
private fun buildMediaTools() {
    addTool("IMPORT MEDIA") { pickMedia() }
    addTool("IMPORT PHOTOS") { pickImage() }
    addTool("IMPORT MUSIC") { pickAudio() }
    addTool("IMPORT FONT") { importFont() }
    addTool("ADD SELECTED TO TIMELINE") { addSelectedAssetToTimeline() }
    addHeader("MEDIA BIN (${state.assets.size})")
    state.assets.forEach { asset ->
        addTool(asset.name.take(30)) { selectAsset(asset.id) }
    }
}

private fun buildEditTools() {
    addTool("ADD TEXT") { addTextLayer() }
    addTool("ADD SHAPE") { addShapeLayer() }
    addTool("SPLIT AT PLAYHEAD") { splitSelected() }
    addTool("SET IN") { setInAtPlayhead() }
    addTool("SET OUT") { setOutAtPlayhead() }
    addTool("DUPLICATE") { duplicateSelected() }
    addTool("DELETE") { timelineView.deleteSelection(ripple = false) }
    addTool("RIPPLE DELETE") { timelineView.deleteSelection(ripple = true) }
    addTool("ADD KEYFRAME") { addKeyframeToSelected() }
    addTool("CLEAR KEYFRAMES") { clearKeyframes() }
    addTool("SELECT ALL") { timelineView.selectAll() }
    addTool("CLEAR SELECTION") { timelineView.clearSelection() }
}

private fun buildFxTools() {
    addHeader("EFFECTS (${EffectRegistry.COUNT})")
    EffectRegistry.ALL.groupBy { it.category }.forEach { (category, effects) ->
        addHeader(category)
        effects.forEach { def ->
            addTool(def.name) { applyEffect(def.name) }
        }
    }
}

private fun buildAiTools() {
    addHeader("AI CAPABILITIES")
    AiRuntime.capabilities().forEach { cap ->
        val label = if (cap.available) cap.name else "🔒 ${cap.name}"
        addTool(label) {
            if (cap.available) runAiCapability(cap)
            else toast("${cap.name}: ${cap.reason}")
        }
    }
}

private fun buildAudioTools() {
    addTool("IMPORT MUSIC") { pickAudio() }
    addTool("BEAT SCAN") { analyzeBeats() }
    addTool("AUTO CUT ON BEATS") { cutOnBeats() }
    addTool("VOLUME +") { adjustVolume(0.1f) }
    addTool("VOLUME −") { adjustVolume(-0.1f) }
    addTool("MUTE SELECTED") { toggleMute() }
}

private fun buildColorTools() {
    addTool("NORMAL") { setColorMode("NORMAL") }
    addTool("B&W") { setColorMode("BW") }
    addTool("INVERT") { setColorMode("INVERT") }
    addHeader("PRESETS")
    addTool("ADD VIGNETTE") { applyEffect("Vignette") }
    addTool("ADD FILM GRAIN") { applyEffect("Film Grain") }
    addTool("ADD CHROMA ABERRATION") { applyEffect("Chroma Shift") }
    addTool("TEAL & ORANGE") { applyEffect("Teal & Orange") }
    addTool("CROSS PROCESS") { applyEffect("Cross Process") }
}

private fun buildTextTools() {
    addTool("ADD TEXT LAYER") { addTextLayer() }
    addHeader("ANIMATION PRESETS (${TextAnimationRegistry.COUNT})")
    TextAnimationRegistry.ALL.forEach { preset ->
        addTool(preset.name) {
            val tl = selectedTextLayer()
            if (tl != null) {
                tl.preset = preset.name
                toast("Preset: ${preset.name}")
            } else {
                toast("Select a text layer first")
            }
        }
    }
}

private fun build3DTools() {
    addTool("OPEN 3D SCENE") { open3D() }
    addHeader("COMING SOON")
    addTool("ADD 3D LAYER") { open3D() }
    addTool("ADD 3D TEXT") { open3D() }
    addTool("RESET CAMERA") { open3D() }
}

private fun buildDeliverTools() {
    addHeader("EXPORT")
    addTool("EXPORT VIDEO") { renderCurrent() }
    addTool("SAVE PROJECT") { saveProject() }
    addTool("OPEN PROJECT") { openProject() }
    addHeader("PRO PIPELINE")
    addTool("${ProModeBlock.statusLabel()}") { toggleProMode() }
}

// -------------------------------------------------------------------------
// Tool panel helpers
// -------------------------------------------------------------------------
private fun addTool(label: String, action: () -> Unit) {
    toolPanel.addView(
        outlineButton(label, action),
        LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(3)
            bottomMargin = dp(3)
        },
    )
}

private fun addHeader(label: String) {
    toolPanel.addView(
        headerLabel(label),
        LinearLayout.LayoutParams(-1, dp(28)).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        },
    )
}
    // =========================================================================
    // TimelineHost implementation
    // =========================================================================
    override val projectLayers: List<Layer2D> get() = state.layers
    override val projectTextLayers: List<TextLayer> get() = state.textLayers
    override val projectAssets: List<MediaAsset> get() = state.assets
    override val projectBeatMarkers: List<BeatMarker> get() = state.beatMarkers
    override val projectUserMarkers: MutableList<UserMarker> get() = state.userMarkers
    override val projectTransitions: MutableList<TransitionPlacement> get() = state.transitions
    override val projectTrackStates: MutableList<TrackState> get() = state.trackStates
    override val projectFps: Int get() = state.fps
    override val projectWidth: Int get() = state.width
    override val projectHeight: Int get() = state.height

    override fun selectedClipIds(): Set<Long> = selectedClipIds
    override fun playheadMs(): Long = state.playheadMs
    override fun isPlaying(): Boolean = state.isPlaying
    override fun isProMode(): Boolean = pipelineMode == ProModeBlock.PipelineMode.PRO

    override fun mutate(block: () -> Unit) {
        state.pushUndo()
        block()
        state.notifyChanged()
        refreshInspector()
        timelineView.invalidateForProjectChange()
        glView?.requestRender()
    }

    override fun setPlayhead(timeMs: Long) {
        state.playheadMs = timeMs.coerceAtLeast(0L)
        renderer?.setPlayheadTime(state.playheadMs)
        glView?.requestRender()
    }

    override fun setSelection(ids: Set<Long>) {
        selectedClipIds.clear()
        selectedClipIds.addAll(ids)
    }

    override fun seekPlaybackTo(timeMs: Long) {
        // Delegated to GlEffectRenderer
    }

    override fun requestSplitAt(timeMs: Long, clipId: Long) {
        val layer = state.layers.firstOrNull { it.id == clipId } ?: return
        if (layer.locked) return toast("Clip is locked")
        if (timeMs <= layer.timelineStartMs + 20 || timeMs >= layer.timelineEndMs() - 20) return
        mutate {
            val local = (timeMs - layer.timelineStartMs).coerceAtLeast(1L)
            val splitSource = layer.sourceInMs + (local * layer.speed).toLong()
            val oldOut = layer.sourceOutMs
            layer.sourceOutMs = splitSource
            val right = layer.copy(
                id = state.nextLayerId++,
                timelineStartMs = timeMs,
                sourceInMs = splitSource,
                sourceOutMs = oldOut,
                effects = layer.effects.toMutableList(),
            )
            state.layers.add(right)
        }
    }

    override fun requestDelete(ids: Set<Long>, ripple: Boolean) {
        mutate {
            val removed = state.layers.filter { it.id in ids }
            state.layers.removeAll { it.id in ids }
            state.textLayers.removeAll { it.id in ids }
            if (ripple) {
                val cut = removed.minOfOrNull { it.timelineStartMs } ?: return@mutate
                val gap = removed.sumOf { it.timelineEndMs() - it.timelineStartMs }
                state.layers.filter { it.timelineStartMs > cut }.forEach {
                    it.timelineStartMs = (it.timelineStartMs - gap).coerceAtLeast(0L)
                }
            }
        }
    }

    override fun requestMoveClip(id: Long, newStartMs: Long, newTrackIndex: Int) {
        val layer = state.layers.firstOrNull { it.id == id } ?: return
        layer.timelineStartMs = newStartMs.coerceAtLeast(0L)
        layer.trackIndex = newTrackIndex
    }

    override fun requestTrimClip(id: Long, newInMs: Long, newOutMs: Long, newStartMs: Long) {
        val layer = state.layers.firstOrNull { it.id == id } ?: return
        layer.sourceInMs = newInMs
        layer.sourceOutMs = newOutMs
        layer.timelineStartMs = newStartMs
    }

    override fun requestAddTransition(leftId: Long, rightId: Long) {
        val idx = TransitionBlock.indexOfDefault()
        mutate {
            state.transitions.add(
                TransitionPlacement(
                    id = state.nextTransitionId++,
                    leftClipId = leftId,
                    rightClipId = rightId,
                    transitionName = TransitionRegistry.ALL[idx].name,
                )
            )
        }
        toast("Transition added")
    }

    override fun requestAddMarker(timeMs: Long, kind: MarkerKind) {
        mutate {
            state.userMarkers.add(
                UserMarker(state.nextMarkerId++, timeMs, kind, kind.name.lowercase())
            )
        }
    }

    override fun assetUri(assetId: Long): Uri? =
        state.assets.firstOrNull { it.id == assetId }?.uri

    override fun assetDurationMs(assetId: Long): Long =
        state.assets.firstOrNull { it.id == assetId }?.durationMs ?: 0L

    override fun toast(msg: String) {
        ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    // =========================================================================
    // Import
    // =========================================================================
    private fun pickMedia() = launchPicker(REQ_MEDIA, "*/*")
    private fun pickImage() = launchPicker(REQ_MEDIA, "image/*")
    private fun pickAudio() = launchPicker(REQ_MEDIA, "audio/*")
    private fun importFont() = launchPicker(REQ_FONT_IMPORT, "*/*")

    private fun launchPicker(req: Int, mime: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, req)
    }

    @Deprecated("Kept dependency-free for easy drop-in use.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_MEDIA -> data.data?.let { importAsset(it) }
            REQ_FONT_IMPORT -> data.data?.let {
                fontManager.import(it) { toast("Font imported") }
            }
            REQ_PROJECT_OPEN -> data.data?.let { openProjectFrom(it) }
            REQ_PROJECT_SAVE -> data.data?.let { saveProjectTo(it) }
        }
    }

    private fun importAsset(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}
        executor.execute {
            val kind = MediaImporter.detectKind(this, uri)
            val name = MediaImporter.displayName(this, uri) ?: "Asset"
            val size = MediaImporter.size(this, uri)
            val duration = MediaImporter.durationMs(this, uri)
            ui.post {
                val asset = MediaAsset(state.nextAssetId++, uri, name, kind, duration, size)
                state.assets.add(asset)
                toast("Imported $name")
                openSheet(currentPage)
            }
        }
    }

    // =========================================================================
    // Playback
    // =========================================================================
    private fun togglePlayback() {
        state.isPlaying = !state.isPlaying
        statusLabel.text = if (state.isPlaying) "PLAYING" else "READY"
    }

    // =========================================================================
    // Project operations
    // =========================================================================
    private fun newProject() {
        AlertDialog.Builder(this)
            .setTitle("New Project")
            .setMessage("Discard the current project?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("NEW") { _, _ ->
                state.reset()
                selectedClipIds.clear()
                switchPage("EDIT")
                toast("New project")
            }
            .show()
    }

    private fun openProject() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            },
            REQ_PROJECT_OPEN
        )
    }

    private fun openProjectFrom(uri: Uri) {
        executor.execute {
            try {
                val json = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("empty")
                ui.post {
                    serializer.deserialize(json, state)
                    switchPage("EDIT")
                    timelineView.invalidateForProjectChange()
                    toast("Project loaded")
                }
            } catch (t: Throwable) {
                ui.post { toast("Load failed: ${t.message}") }
            }
        }
    }

    private fun saveProject() {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "MotionStudio_Project.json")
            },
            REQ_PROJECT_SAVE
        )
    }

    private fun saveProjectTo(uri: Uri) {
        executor.execute {
            try {
                val json = serializer.serialize(state)
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ui.post { toast("Project saved") }
            } catch (t: Throwable) {
                ui.post { toast("Save failed: ${t.message}") }
            }
        }
    }

    // =========================================================================
    // Inspector — no-op for now, sheet handles tool UIs
    // =========================================================================
    private fun refreshInspector() {
        // Inspector panel removed in the CapCut-style layout.
        // Selection-driven UIs will surface inside the EDIT sheet.
    }

    // =========================================================================
    // Text
    // =========================================================================
    private fun addTextLayer() {
        mutate {
            val tl = TextLayer(
                id = state.nextTextLayerId++,
                timelineStartMs = state.playheadMs,
                timelineEndMs = state.playheadMs + 3000L,
            )
            state.textLayers.add(tl)
            selectedClipIds.clear()
            selectedClipIds.add(tl.id)
        }
    }

    private fun addShapeLayer() {
        mutate {
            state.overlays.add(
                OverlaySpec(state.nextOverlayId++, "SHAPE", x = 0.5f, y = 0.5f)
            )
        }
    }

    private fun promptTextEdit(tl: TextLayer) {
        val ed = EditText(this).apply {
            setText(tl.text)
            setTextColor(WHITE)
            setBackgroundColor(BLACK)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Text")
            .setView(ed)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OK") { _, _ ->
                mutate { tl.text = ed.text.toString() }
            }
            .show()
    }

    private fun selectedTextLayer(): TextLayer? =
        state.textLayers.firstOrNull { it.id in selectedClipIds }

    // =========================================================================
    // Effects
    // =========================================================================
    private fun applyEffect(name: String) {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds }
        if (layer == null && selectedTextLayer() == null) {
            return toast("Select a clip first")
        }
        mutate {
            layer?.effects?.add(EffectInstance(state.nextEffectId++, name))
        }
        toast("Applied: $name")
    }

    private fun setColorMode(mode: String) {
        toast("Color mode: $mode")
    }

    // =========================================================================
    // Audio + AI
    // =========================================================================
    private fun analyzeBeats() {
        toast("Beat analysis: delegated to BeatScanner")
    }

    private fun cutOnBeats() {
        toast("Auto cut on beats: delegated to BeatScanner")
    }

    private fun detectScenes() {
        toast("Scene detection: delegated to BeatScanner")
    }

    private fun adjustVolume(delta: Float) {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        mutate { layer.volume = (layer.volume + delta).coerceIn(0f, 2f) }
    }

    private fun toggleMute() {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        mutate { layer.muted = !layer.muted }
    }

    private fun runAiCapability(cap: AiRuntime.Capability) {
        toast("Running ${cap.name}…")
    }

    // =========================================================================
    // Node / 3D
    // =========================================================================
    private fun openNodes() { nodeOverlay.visibility = View.VISIBLE }
    private fun closeNodes() { nodeOverlay.visibility = View.GONE }
    private fun open3D() { scene3DOverlay.visibility = View.VISIBLE }
    private fun close3D() { scene3DOverlay.visibility = View.GONE }

    // =========================================================================
    // Viewer helpers
    // =========================================================================
    private fun fitViewer() { toast("Fit") }
    private fun resetViewer() { toast("Reset") }

    // =========================================================================
    // Pro mode
    // =========================================================================
    private fun toggleProMode() {
        ProModeBlock.showToggleDialog(this) { enabled ->
            pipelineMode = if (enabled) ProModeBlock.PipelineMode.PRO
                           else ProModeBlock.PipelineMode.CONSUMER
            statusLabel.text = ProModeBlock.statusLabel()
        }
    }

    // =========================================================================
    // Edit operations
    // =========================================================================
    private fun splitSelected() {
        val id = selectedClipIds.firstOrNull() ?: return toast("Select a clip")
        requestSplitAt(state.playheadMs, id)
    }

    private fun setInAtPlayhead() {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        if (state.playheadMs >= layer.timelineEndMs()) return
        mutate {
            val mapped = layer.sourceInMs + (state.playheadMs - layer.timelineStartMs)
            layer.sourceInMs = mapped.coerceIn(0L, layer.sourceOutMs - 1)
            layer.timelineStartMs = state.playheadMs
        }
    }

    private fun setOutAtPlayhead() {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        if (state.playheadMs <= layer.timelineStartMs) return
        mutate {
            val mapped = layer.sourceInMs + (state.playheadMs - layer.timelineStartMs)
            layer.sourceOutMs = mapped.coerceAtLeast(layer.sourceInMs + 1)
        }
    }

    private fun duplicateSelected() {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        mutate {
            val dup = layer.copy(
                id = state.nextLayerId++,
                timelineStartMs = layer.timelineEndMs() + 100,
                effects = layer.effects.map { it.copy(id = state.nextEffectId++) }.toMutableList(),
            )
            state.layers.add(dup)
        }
    }

    private fun addKeyframeToSelected() {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        mutate {
            layer.props.forEach { (_, track) ->
                if (track.keys.none { Math.abs(it.timeMs - state.playheadMs) < 20 }) {
                    track.keys.add(Keyframe(state.playheadMs, track.sample(state.playheadMs)))
                }
            }
        }
    }

    private fun clearKeyframes() {
        val layer = state.layers.firstOrNull { it.id in selectedClipIds } ?: return
        mutate { layer.props.forEach { (_, track) -> track.keys.clear() } }
    }

    private fun selectAsset(id: Long) {
        selectedAssetId = id
        state.assetById(id)?.let { toast("Selected: ${it.name}") }
    }

    private fun addSelectedAssetToTimeline() {
        val asset = selectedAssetId?.let { state.assetById(it) }
            ?: state.assets.lastOrNull()
            ?: return toast("Import a media file first")
        mutate {
            val layer = Layer2D(
                id = state.nextLayerId++,
                assetId = asset.id,
                timelineStartMs = state.playheadMs,
                sourceInMs = 0,
                sourceOutMs = asset.durationMs.coerceAtLeast(1000L),
                trackIndex = if (asset.kind == AssetKind.AUDIO) 5 else 0,
            )
            state.layers.add(layer)
            selectedClipIds.clear()
            selectedClipIds.add(layer.id)
        }
        closeSheet()
        toast("Added to timeline")
    }

    private fun renderCurrent() {
        toast("Render: delegated to RenderEngine")
    }

    private fun undo() {
        state.undo()
        timelineView.invalidateForProjectChange()
    }

    private fun redo() {
        state.redo()
        timelineView.invalidateForProjectChange()
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun textLabel(text: String, sp: Float): TextView = TextView(this).apply {
        this.text = text
        setTextColor(WHITE)
        textSize = sp
    }

    private fun headerLabel(text: String): TextView = textLabel(text, 10f).apply {
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundColor(Color.rgb(28, 28, 28))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
    }

    private fun outlineButton(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        setTextColor(WHITE)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(18, 18, 18))
            setStroke(dp(1f).toInt(), Color.rgb(90, 90, 90))
            cornerRadius = dp(6f)
        }
        setOnClickListener { action() }
    }

    private fun buttonLp(width: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(width), dp(32))

    private fun fillLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
        }

    private fun seekRow(
        label: String, min: Int, max: Int, current: Int,
        onChange: (Int) -> Unit,
    ): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val value = textLabel(current.toString(), 9f).apply { gravity = Gravity.RIGHT }
        top.addView(textLabel(label, 9f), LinearLayout.LayoutParams(0, dp(20), 1f))
        top.addView(value, LinearLayout.LayoutParams(dp(60), dp(20)))
        row.addView(top)
        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = (current - min).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val real = p + min
                    value.text = real.toString()
                    if (fromUser) onChange(real)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(sb, LinearLayout.LayoutParams(-1, dp(28)))
        return row
    }
}

package com.motionstudio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

/**
 * MainActivity — the orchestrator.
 *
 * Owns the six pages, the shared viewer, the timeline, and the inspector.
 * Delegates:
 *   - timeline drawing/hit-testing → Timeline.kt (TimelineView)
 *   - video/GPU rendering          → GlEffectRenderer.kt
 *   - text rendering               → TextAnimator.kt
 *   - 3D scene                     → Scene3DRenderer.kt
 *   - save/load                    → ProjectSerializer.kt
 *   - fonts                        → FontManager.kt
 *   - pro mode toggle              → ProModeBlock.kt
 *   - AI capability probe          → AiRuntime.kt
 *   - text/subtitle logic          → TextBlock.kt
 *   - transitions runtime          → TransitionBlock.kt
 */
class MainActivity : Activity(), TimelineHost {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    private val BLACK = Color.BLACK
    private val WHITE = Color.WHITE
    private val GRAY = Color.rgb(128, 128, 128)

    private val REQ_MEDIA = 1001
    private val REQ_PROJECT_OPEN = 1004
    private val REQ_PROJECT_SAVE = 1005
    private val REQ_FONT_IMPORT = 1006

    private val executor = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // Project state (single source of truth)
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
    private lateinit var topBar: LinearLayout
    private lateinit var mainBody: LinearLayout
    private lateinit var leftPanel: LinearLayout
    private lateinit var centerPanel: LinearLayout
    private lateinit var rightPanel: LinearLayout
    private lateinit var toolPanel: LinearLayout
    private lateinit var inspectorScroll: ScrollView
    private lateinit var inspectorBody: LinearLayout
    private lateinit var viewerFrame: FrameLayout
    private lateinit var timelineView: TimelineView
    private lateinit var timelineScroll: HorizontalScrollView
    private lateinit var statusLabel: TextView
    private lateinit var nodeOverlay: FrameLayout
    private lateinit var scene3DOverlay: FrameLayout
    private lateinit var overlayLayer: FrameLayout
    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ProModeBlock.initialize()
        AiRuntime.initialize(this)

        buildUi()
        goImmersive()

        // Wire 1: register as a listener so state changes refresh the UI
        state.addListener(object : ProjectState.Listener {
            override fun onProjectChanged() {
                refreshInspector()
                if (::timelineView.isInitialized) timelineView.invalidateForProjectChange()
            }
        })

        // Wire 2: assign the serializer codec so undo/redo works
        state.codec = object : ProjectState.SnapshotCodec {
            override fun encode(state: ProjectState) = serializer.serialize(state)
            override fun decode(json: String, into: ProjectState) =
                serializer.deserialize(json, into)
        }
    }

    override fun onResume() { super.onResume(); goImmersive() }
    override fun onPause() { super.onPause() }
    override fun onDestroy() {
    renderer?.release()
    executor.shutdownNow()
    super.onDestroy()
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

        topBar = buildTopBar()
        column.addView(topBar, LinearLayout.LayoutParams(-1, dp(48)))

        mainBody = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BLACK)
        }
        column.addView(mainBody, LinearLayout.LayoutParams(-1, 0, 1f))

        leftPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        mainBody.addView(leftPanel, LinearLayout.LayoutParams(dp(165), -1))

        centerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        mainBody.addView(centerPanel, LinearLayout.LayoutParams(0, -1, 1f))

        rightPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        mainBody.addView(rightPanel, LinearLayout.LayoutParams(dp(190), -1))

        buildLeftPanel()
        buildCenterPanel()
        buildRightPanel()

        val timelinePanel = buildTimelinePanel()
        column.addView(timelinePanel, LinearLayout.LayoutParams(-1, dp(140)))

        buildNodeOverlay()
        build3DOverlay()

        switchPage("EDIT")
    }

    private fun buildTopBar(): LinearLayout {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(BLACK)
        setPadding(dp(8), 0, dp(8), 0)
    }
    bar.addView(textLabel("MOTION STUDIO", 12f).apply {
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), 0, dp(8), 0)
    }, LinearLayout.LayoutParams(-2, -1))

    bar.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f))

    statusLabel = textLabel("READY", 10f).apply { gravity = Gravity.CENTER }
    bar.addView(statusLabel, LinearLayout.LayoutParams(dp(90), -1))

    // The two overlay toggles — top right
    bar.addView(outlineButton("TOOLS") { toggleLeftPanel() }, buttonLp(62))
    bar.addView(outlineButton("INSPECT") { toggleRightPanel() }, buttonLp(70))

    bar.addView(outlineButton("NEW") { newProject() }, buttonLp(48))
    bar.addView(outlineButton("SAVE") { saveProject() }, buttonLp(52))
    bar.addView(outlineButton("OPEN") { openProject() }, buttonLp(52))
    bar.addView(outlineButton("RENDER") { renderCurrent() }, buttonLp(64))
    return bar
    }
        return bar
    }
    private fun buildLeftPanel() {
    leftPanel.removeAllViews()

    val tabs = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
    }
    val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    tabs.addView(tabRow)
    listOf("MEDIA", "EDIT", "FX", "AI", "AUDIO", "COLOR", "3D").forEach { tab ->
        tabRow.addView(outlineButton(tab) { selectTool(tab) }, buttonLp(72))
    }
    leftPanel.addView(tabs, LinearLayout.LayoutParams(-1, dp(36)))
    leftPanel.addView(headerLabel("TOOLS"), LinearLayout.LayoutParams(-1, dp(24)))

    toolPanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), dp(4), dp(6), dp(8))
    }
    val scroll = ScrollView(this).apply { isFillViewport = true }
    scroll.addView(toolPanel)
    leftPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
}

private fun buildCenterPanel() {
    centerPanel.removeAllViews()
    viewerFrame = FrameLayout(this).apply { setBackgroundColor(BLACK) }
    centerPanel.addView(viewerFrame, LinearLayout.LayoutParams(-1, 0, 1f))
// In buildCenterPanel, before the GLSurfaceView is added:
val hint = textLabel("Tap IMPORT to add media, or TOOLS to see options", 11f).apply {
    setTextColor(GRAY)
    gravity = Gravity.CENTER
    setPadding(dp(20), dp(20), dp(20), dp(20))
}
viewerFrame.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    // Wire 6: mount the GL viewer
    val r = GlEffectRenderer(this, state)
renderer = r
glView = android.opengl.GLSurfaceView(this).apply {
    setEGLContextClientVersion(2)
    setRenderer(r)
    renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
    preserveEGLContextOnPause = true
}
viewerFrame.addView(glView, FrameLayout.LayoutParams(-1, -1))
    val hud = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    viewerFrame.addView(hud, FrameLayout.LayoutParams(-1, dp(32), Gravity.TOP))
    hud.addView(textLabel("VIEWER", 9f).apply { typeface = Typeface.DEFAULT_BOLD },
        LinearLayout.LayoutParams(0, -1, 1f))
    hud.addView(outlineButton("FIT") { fitViewer() }, buttonLp(52))
    hud.addView(outlineButton("1:1") { resetViewer() }, buttonLp(52))

    val transport = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(4), dp(6), dp(4))
    }
    centerPanel.addView(transport, LinearLayout.LayoutParams(-1, dp(44)))
    transport.addView(outlineButton("|<") { timelineView.jumpToPrevEdit() }, buttonLp(48))
    transport.addView(outlineButton("PLAY") { togglePlayback() }, buttonLp(64))
    transport.addView(outlineButton(">|") { timelineView.jumpToNextEdit() }, buttonLp(48))
    transport.addView(outlineButton("MARK") { timelineView.addUserMarker() }, buttonLp(60))
    transport.addView(outlineButton("UNDO") { undo() }, buttonLp(60))
    transport.addView(outlineButton("REDO") { redo() }, buttonLp(60))
}

private fun buildRightPanel() {
    rightPanel.removeAllViews()
    rightPanel.addView(headerLabel("INSPECTOR"), LinearLayout.LayoutParams(-1, dp(24)))
    inspectorScroll = ScrollView(this).apply { setBackgroundColor(BLACK) }
    inspectorBody = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(12))
    }
    inspectorScroll.addView(inspectorBody)
    rightPanel.addView(inspectorScroll, LinearLayout.LayoutParams(-1, 0, 1f))
    refreshInspector()
}

private fun buildTimelinePanel(): LinearLayout {
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BLACK)
    }

    timelineScroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
    }
    timelineView = TimelineView(this, this)
    timelineScroll.addView(timelineView, FrameLayout.LayoutParams(-2, -1))
    panel.addView(timelineScroll, LinearLayout.LayoutParams(-1, 0, 1f))

    val stripScroll = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
}
val strip = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(6), dp(3), dp(6), dp(3))
}
strip.addView(outlineButton("IMPORT") { pickMedia() }, buttonLp(64))
strip.addView(outlineButton("AUDIO") { pickAudio() }, buttonLp(56))
strip.addView(outlineButton("IMAGE") { pickImage() }, buttonLp(56))
strip.addView(outlineButton("ADD") { addSelectedAssetToTimeline() }, buttonLp(52))
strip.addView(outlineButton("BEATS") { analyzeBeats() }, buttonLp(58))
strip.addView(outlineButton("SCENES") { detectScenes() }, buttonLp(64))
strip.addView(outlineButton("FONT") { importFont() }, buttonLp(52))
stripScroll.addView(strip)
panel.addView(stripScroll, LinearLayout.LayoutParams(-1, dp(34)))
    return panel
}

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
    header.addView(outlineButton("CLOSE") { closeNodes() }, buttonLp(64))
    nodeOverlay.addView(header, FrameLayout.LayoutParams(-1, dp(40)))
    val graphView = View(this).apply { setBackgroundColor(BLACK) }
    nodeOverlay.addView(graphView, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(40) })
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
    header.addView(outlineButton("CLOSE") { close3D() }, buttonLp(64))
    scene3DOverlay.addView(header, FrameLayout.LayoutParams(-1, dp(40)))
}
// =========================================================================
// Page switching
// =========================================================================
private fun switchPage(page: String) {
    currentPage = page
    closeNodes(); close3D()
    when (page) {
        "EDIT" -> selectTool("EDIT")
        "TEXT" -> selectTool("TEXT")
        "FX" -> selectTool("FX")
        "NODES" -> openNodes()
        "3D" -> open3D()
        "DELIVER" -> selectTool("DELIVER")
    }
}
private fun toggleLeftPanel() {
    leftPanel.visibility = if (leftPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    if (leftPanel.visibility == View.VISIBLE) rightPanel.visibility = View.GONE
}

private fun toggleRightPanel() {
    rightPanel.visibility = if (rightPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    if (rightPanel.visibility == View.VISIBLE) leftPanel.visibility = View.GONE
}
private fun selectTool(tool: String) {
    toolPanel.removeAllViews()
    when (tool) {
        "MEDIA" -> buildMediaTools()
        "EDIT" -> buildEditTools()
        "FX" -> buildFxTools()
        "AI" -> buildAiTools()
        "AUDIO" -> buildAudioTools()
        "COLOR" -> buildColorTools()
        "TEXT" -> buildTextTools()
        "DELIVER" -> buildDeliverTools()
    }
}

// =========================================================================
// Tool panels
// =========================================================================
private fun buildMediaTools() {
    addTool("IMPORT MEDIA") { pickMedia() }
    addTool("IMPORT PHOTOS") { pickImage() }
    addTool("IMPORT MUSIC") { pickAudio() }
    addTool("IMPORT FONT") { importFont() }
    addTool("ADD TO TIMELINE") { addSelectedAssetToTimeline() }
    addHeader("MEDIA BIN")
    state.assets.forEach { asset ->
        addTool(asset.name.take(24)) { selectAsset(asset.id) }
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
    addHeader("AI")
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
    addTool("MUTE") { toggleMute() }
}

private fun buildColorTools() {
    addTool("NORMAL") { setColorMode("NORMAL") }
    addTool("B&W") { setColorMode("BW") }
    addTool("INVERT") { setColorMode("INVERT") }
    addTool("ADD VIGNETTE") { applyEffect("Vignette") }
    addTool("ADD FILM GRAIN") { applyEffect("Film Grain") }
    addTool("ADD CHROMA ABERRATION") { applyEffect("Chroma Shift") }
}

private fun buildTextTools() {
    addTool("ADD TEXT LAYER") { addTextLayer() }
    addHeader("ANIMATION PRESETS")
    TextAnimationRegistry.ALL.forEach { preset ->
        addTool(preset.name) {
            selectedTextLayer()?.let {
                it.preset = preset.name
                toast("Preset: ${preset.name}")
            } ?: toast("Select a text layer first")
        }
    }
}

private fun buildDeliverTools() {
    addTool("EXPORT VIDEO") { renderCurrent() }
    addTool("SAVE PROJECT") { saveProject() }
    addTool("OPEN PROJECT") { openProject() }
    addHeader("STATUS")
    addTool("Pipeline: ${ProModeBlock.statusLabel()}") {}
}
// =========================================================================
// TimelineHost implementation — the contract with Timeline.kt
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
    updatePlayheadUi()
    renderer?.setPlayheadTime(state.playheadMs)
    glView?.requestRender()
}
override fun setSelection(ids: Set<Long>) {
    selectedClipIds.clear()
    selectedClipIds.addAll(ids)
}

override fun seekPlaybackTo(timeMs: Long) {
    // Delegated to GlEffectRenderer in full build
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
// Import / pick
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
            selectTool(currentPage.lowercase().replaceFirstChar { it.uppercase() })
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
// Inspector
// =========================================================================
private fun refreshInspector() {
    inspectorBody.removeAllViews()
    val selected = state.layers.firstOrNull { it.id in selectedClipIds }
    if (selected != null) buildLayerInspector(selected)
    val textLayer = state.textLayers.firstOrNull { it.id in selectedClipIds }
    if (textLayer != null) buildTextInspector(textLayer)
    if (selected == null && textLayer == null) {
        inspectorBody.addView(
            textLabel("Select a clip or text layer", 10f).apply {
                setTextColor(GRAY)
                setPadding(0, dp(12), 0, 0)
            }
        )
    }
}

private fun buildLayerInspector(layer: Layer2D) {
    inspectorBody.addView(
        headerLabel("LAYER ${layer.id}"),
        LinearLayout.LayoutParams(-1, dp(24))
    )
    inspectorBody.addView(seekRow("SPEED", 25, 400, (layer.speed * 100).toInt()) {
        layer.speed = it / 100f
        timelineView.invalidate()
    })
    inspectorBody.addView(seekRow("VOLUME", 0, 200, (layer.volume * 100).toInt()) {
        layer.volume = it / 100f
    })
    inspectorBody.addView(
        headerLabel("TRANSFORM KEYS"),
        LinearLayout.LayoutParams(-1, dp(24))
    )
    layer.props.forEach { (channel, track) ->
        inspectorBody.addView(propertyRow(layer.id, channel, track))
    }
    if (layer.effects.isNotEmpty()) {
        inspectorBody.addView(headerLabel("EFFECTS"), LinearLayout.LayoutParams(-1, dp(24)))
        layer.effects.forEach { fx ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(textLabel(fx.type, 10f), LinearLayout.LayoutParams(0, -1, 1f))
            row.addView(outlineButton("X") {
                mutate { layer.effects.remove(fx) }
            }, buttonLp(36))
            inspectorBody.addView(row, LinearLayout.LayoutParams(-1, dp(32)))
        }
    }
}

private fun buildTextInspector(tl: TextLayer) {
    inspectorBody.addView(headerLabel("TEXT LAYER"), LinearLayout.LayoutParams(-1, dp(24)))
    inspectorBody.addView(outlineButton("EDIT TEXT") { promptTextEdit(tl) }, fillLp())
    inspectorBody.addView(seekRow("SIZE", 12, 300, tl.fontSize.toInt()) {
        tl.fontSize = it.toFloat()
    })
    inspectorBody.addView(seekRow("TRACKING", -20, 40, tl.tracking.toInt()) {
        tl.tracking = it.toFloat()
    })
    inspectorBody.addView(headerLabel("PRESET"), LinearLayout.LayoutParams(-1, dp(24)))
    TextAnimationRegistry.ALL.take(12).chunked(4).forEach { group ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        group.forEach { preset ->
            row.addView(
                outlineButton(preset.name) { tl.preset = preset.name },
                LinearLayout.LayoutParams(0, dp(32), 1f)
            )
        }
        inspectorBody.addView(row, LinearLayout.LayoutParams(-1, dp(34)))
    }
}

private fun propertyRow(layerId: Long, channel: String, track: PropertyTrack): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    row.addView(outlineButton("◆") {
        mutate {
            val exists = track.keys.firstOrNull {
                Math.abs(it.timeMs - state.playheadMs) < 20
            }
            if (exists != null) track.keys.remove(exists)
            else track.keys.add(Keyframe(state.playheadMs, track.sample(state.playheadMs)))
        }
        timelineView.invalidate()
    }, buttonLp(30))
    row.addView(
        textLabel(channel.uppercase(Locale.US), 9f),
        LinearLayout.LayoutParams(dp(60), -1)
    )
    val current = track.sample(state.playheadMs)
    val sb = SeekBar(this).apply {
        max = 400
        progress = (current + 200).toInt().coerceIn(0, 400)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = p - 200f
                val key = track.keys.firstOrNull {
                    Math.abs(it.timeMs - state.playheadMs) < 20
                }
                if (key != null) key.value = v else track.baseValue = v
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
    row.addView(sb, LinearLayout.LayoutParams(0, dp(32), 1f))
    return row
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
    // Viewer
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

    private fun updatePlayheadUi() {
        // Timestamp is drawn by TimelineView
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
        setBackgroundColor(Color.rgb(20, 20, 20))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun outlineButton(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        setTextColor(WHITE)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(BLACK)
            setStroke(dp(1f).toInt(), WHITE)
            cornerRadius = dp(4f)
        }
        setOnClickListener { action() }
    }

    private fun buttonLp(width: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(width), dp(32))

    private fun fillLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
        }

    private fun addTool(label: String, action: () -> Unit) {
        toolPanel.addView(outlineButton(label, action), fillLp())
    }

    private fun addHeader(label: String) {
        toolPanel.addView(headerLabel(label), LinearLayout.LayoutParams(-1, dp(24)))
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

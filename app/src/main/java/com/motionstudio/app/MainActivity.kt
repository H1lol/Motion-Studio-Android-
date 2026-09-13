package com.motionstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ==========================================
// SECTION 1: THEME & COLOR PALETTE CONSTANTS
// ==========================================
object MotionTheme {
    val Obsidian = Color(0xFF0D0E12)
    val SurfaceDark = Color(0xFF16181D)
    val SurfaceElevated = Color(0xFF1E2129)
    val SurfaceHighlight = Color(0xFF2A2E39)
    val ElectricBlue = Color(0xFF3B82F6)
    val NeonViolet = Color(0xFF8B5CF6)
    val lage TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9CA3AF)
    val BorderColor = Color(0xFF2D3139)
    val AccentGreen = Color(0xFF10B981)
    val AccentRed = Color(0xFFEF4444)
    val AccentAmber = Color(0xFFF59E0B)
}

// ==========================================
// SECTION 2: DATA STRUCTURES & MODELS
// ==========================================
enum class InterpolationType { LINEAR, BEZIER, HOLD }

data class BezierHandle(
    val x: Float,
    val y: Float
)

data class Keyframe<T>(
    val id: String,
    val timeMs: Long,
    val value: T,
    val easeIn: BezierHandle,
    val easeOut: BezierHandle,
    val interpolation: InterpolationType
)

data class KeyframeChannel<T>(
    val propertyName: String,
    val keyframes: List<Keyframe<T>>
)

// "video" | "shape" | "text" | "adjustment" | "null"
// "null" = a Null Object: an invisible transform-only node used to parent/group
// other layers' motion, same concept as AE/Resolve Fusion nulls.
data class Layer(
    val id: String,
    val name: String,
    val type: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val transform: LayerTransform,
    val effects: LayerEffects
)

data class LayerTransform(
    val positionX: KeyframeChannel<Float>,
    val positionY: KeyframeChannel<Float>,
    val scale: KeyframeChannel<Float>,
    val rotation: KeyframeChannel<Float>,
    val opacity: KeyframeChannel<Float>
)

data class LayerEffects(
    val glitchIntensity: KeyframeChannel<Float>,
    val blurRadius: KeyframeChannel<Float>
)

data class Track(
    val id: String,
    val name: String,
    val layers: List<Layer>
)

data class ColorGradingState(
    val liftR: Float = 0f, val liftG: Float = 0f, val liftB: Float = 0f,
    val gammaR: Float = 1f, val gammaG: Float = 1f, val gammaB: Float = 1f,
    val gainR: Float = 1f, val gainG: Float = 1f, val gainB: Float = 1f,
    val temperature: Float = 5000f,
    val tint: Float = 0f,
    val saturation: Float = 1.0f
)

// Available editable channels on LayerTransform, keyed by string id so the
// graph editor / timeline can address "which curve" generically.
val TRANSFORM_CHANNEL_KEYS = listOf("positionX", "positionY", "scale", "rotation", "opacity")

fun LayerTransform.getChannel(key: String): KeyframeChannel<Float> = when (key) {
    "positionX" -> positionX
    "positionY" -> positionY
    "scale" -> scale
    "rotation" -> rotation
    "opacity" -> opacity
    else -> positionX
}

fun LayerTransform.withChannel(key: String, updated: KeyframeChannel<Float>): LayerTransform = when (key) {
    "positionX" -> copy(positionX = updated)
    "positionY" -> copy(positionY = updated)
    "scale" -> copy(scale = updated)
    "rotation" -> copy(rotation = updated)
    "opacity" -> copy(opacity = updated)
    else -> this
}

fun emptyTransform(): LayerTransform = LayerTransform(
    positionX = KeyframeChannel("Position X", emptyList()),
    positionY = KeyframeChannel("Position Y", emptyList()),
    scale = KeyframeChannel("Scale", emptyList()),
    rotation = KeyframeChannel("Rotation", emptyList()),
    opacity = KeyframeChannel("Opacity", emptyList())
)

fun emptyEffects(): LayerEffects = LayerEffects(
    glitchIntensity = KeyframeChannel("Glitch", emptyList()),
    blurRadius = KeyframeChannel("Blur", emptyList())
)

// ==========================================
// SECTION 3: MATHEMATICAL SOLVER & ENGINE
// ==========================================
object AnimationMathEngine {
    fun solveCubicBezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        if (t <= 0f) return p0
        if (t >= 1f) return p3

        var u = t
        for (i in 0..5) {
            val ux = 3 * (1 - u) * (1 - u) * u * p1 + 3 * (1 - u) * u * u * p2 + u * u * u - t
            val dux = 3 * (1 - u) * (1 - u) * p1 + 6 * (1 - u) * u * (p2 - p1) + 3 * u * u * (1 - p2)
            if (abs(dux) < 1e-6f) break
            u -= ux / dux
        }

        val py = 3 * (1 - u) * (1 - u) * (1 - u) * 0f +
                 3 * (1 - u) * (1 - u) * u * p1 +
                 3 * (1 - u) * u * u * p2 +
                 u * u * u * 1f
        return py
    }

    fun evaluateFloatChannel(channel: KeyframeChannel<Float>, timeMs: Long): Float {
        val kfs = channel.keyframes
        if (kfs.isEmpty()) return 0f
        if (timeMs <= kfs.first().timeMs) return kfs.first().value
        if (timeMs >= kfs.last().timeMs) return kfs.last().value

        for (i in 0 until kfs.size - 1) {
            val k1 = kfs[i]
            val k2 = kfs[i + 1]
            if (timeMs in k1.timeMs..k2.timeMs) {
                if (k1.interpolation == InterpolationType.HOLD) return k1.value
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs).toFloat()
                val solvedProgress = if (k1.interpolation == InterpolationType.LINEAR) {
                    progress
                } else {
                    solveCubicBezier(progress, k1.easeOut.x, k1.easeOut.y, k2.easeIn.x, k2.easeIn.y)
                }
                return k1.value + solvedProgress * (k2.value - k1.value)
            }
        }
        return kfs.last().value
    }
}

// ==========================================
// SECTION 4: STATE MANAGEMENT VIEWMODEL
// ==========================================
class CompositionViewModel : ViewModel() {
    private val _compositionName = MutableStateFlow("Enterprise Master Composition v3.0")
    val compositionName: StateFlow<String> = _compositionName.asStateFlow()

    private val _durationMs = MutableStateFlow(20000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(3500L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _zoomScale = MutableStateFlow(0.4f) // pixels per ms
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    private val _activePanel = MutableStateFlow("timeline") // timeline, graph, color, effects, inspector
    val activePanel: StateFlow<String> = _activePanel.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>("layer_1")
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    // Multi-select support, needed for merging multiple clips at once.
    private val _selectedLayerIds = MutableStateFlow<Set<String>>(setOf("layer_1"))
    val selectedLayerIds: StateFlow<Set<String>> = _selectedLayerIds.asStateFlow()

    // Which transform channel the Graph Editor is currently plotting.
    private val _selectedChannel = MutableStateFlow("scale")
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _colorGrading = MutableStateFlow(ColorGradingState())
    val colorGrading: StateFlow<ColorGradingState> = _colorGrading.asStateFlow()

    private var nullLayerCounter = 1

    private val _tracks = MutableStateFlow(
        listOf(
            Track(
                id = "track_1",
                name = "V1 - Cinematic Drone Footage",
                layers = listOf(
                    Layer(
                        id = "layer_1",
                        name = "Cyberpunk_Drone_4K.mp4",
                        type = "video",
                        startTimeMs = 0L,
                        durationMs = 15000L,
                        transform = LayerTransform(
                            positionX = KeyframeChannel("Position X", listOf(
                                Keyframe("k1", 0L, 0f, BezierHandle(0f, 0f), BezierHandle(0.33f, 0.33f), InterpolationType.BEZIER),
                                Keyframe("k2", 7500L, 1920f, BezierHandle(0.66f, 0.66f), BezierHandle(1f, 1f), InterpolationType.BEZIER)
                            )),
                            positionY = KeyframeChannel("Position Y", emptyList()),
                            scale = KeyframeChannel("Scale", listOf(
                                Keyframe("k3", 0L, 100f, BezierHandle(0f, 0f), BezierHandle(0.5f, 0.5f), InterpolationType.BEZIER),
                                Keyframe("k4", 15000L, 150f, BezierHandle(0.5f, 0.5f), BezierHandle(1f, 1f), InterpolationType.BEZIER)
                            )),
                            rotation = KeyframeChannel("Rotation", emptyList()),
                            opacity = KeyframeChannel("Opacity", listOf(
                                Keyframe("k5", 0L, 0f, BezierHandle(0f, 0f), BezierHandle(0.2f, 1f), InterpolationType.BEZIER),
                                Keyframe("k6", 2000L, 100f, BezierHandle(0.8f, 0f), BezierHandle(1f, 1f), InterpolationType.BEZIER)
                            ))
                        ),
                        effects = LayerEffects(
                            glitchIntensity = KeyframeChannel("Glitch", listOf(
                                Keyframe("k7", 5000L, 0f, BezierHandle(0f, 0f), BezierHandle(0.5f, 0.5f), InterpolationType.BEZIER),
                                Keyframe("k8", 5500L, 0.85f, BezierHandle(0.5f, 0.5f), BezierHandle(1f, 1f), InterpolationType.BEZIER),
                                Keyframe("k9", 6000L, 0f, BezierHandle(0f, 0f), BezierHandle(1f, 1f), InterpolationType.BEZIER)
                            )),
                            blurRadius = KeyframeChannel("Blur", emptyList())
                        )
                    )
                )
            ),
            Track(
                id = "track_2",
                name = "V2 - Neural HUD Overlay",
                layers = listOf(
                    Layer(
                        id = "layer_2",
                        name = "HUD_SciFi_Elements.mov",
                        type = "shape",
                        startTimeMs = 4000L,
                        durationMs = 12000L,
                        transform = emptyTransform(),
                        effects = emptyEffects()
                    )
                )
            ),
            Track(
                id = "track_3",
                name = "A1 - Industrial Cyber Audio",
                layers = emptyList()
            )
        )
    )
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private var playbackJob: Job? = null

    fun setCurrentPosition(pos: Long) {
        _currentPositionMs.value = pos.coerceIn(0L, _durationMs.value)
    }

    fun setIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (playing) {
            startPlaybackLoop()
        } else {
            playbackJob?.cancel()
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (_isPlaying.value) {
                val now = System.currentTimeMillis()
                val delta = now - lastTime
                lastTime = now

                val nextPos = _currentPositionMs.value + delta
                if (nextPos >= _durationMs.value) {
                    _currentPositionMs.value = 0L
                    _isPlaying.value = false
                    break
                } else {
                    _currentPositionMs.value = nextPos
                }
                delay(16L) // ~60 FPS ticker
            }
        }
    }

    fun setZoomScale(scale: Float) {
        _zoomScale.value = scale.coerceIn(0.1f, 3.0f)
    }

    fun setActivePanel(panel: String) {
        _activePanel.value = panel
    }

    fun setSelectedChannel(channel: String) {
        _selectedChannel.value = channel
    }

    fun selectLayer(id: String?) {
        _selectedLayerId.value = id
        _selectedLayerIds.value = if (id == null) emptySet() else setOf(id)
    }

    /** Toggle a layer in/out of the multi-select set (used for merge/delete). */
    fun toggleLayerSelection(id: String) {
        val current = _selectedLayerIds.value
        _selectedLayerIds.value = if (current.contains(id)) current - id else current + id
        _selectedLayerId.value = id
    }

    fun updateColorGrading(update: (ColorGradingState) -> ColorGradingState) {
        _colorGrading.value = update(_colorGrading.value)
    }

    // -------------------------------------------
    // Null objects
    // -------------------------------------------
    fun addNullLayer(trackId: String) {
        val newLayer = Layer(
            id = "null_${System.currentTimeMillis()}",
            name = "Null ${nullLayerCounter++}",
            type = "null",
            startTimeMs = _currentPositionMs.value,
            durationMs = 5000L,
            transform = emptyTransform(),
            effects = emptyEffects()
        )
        _tracks.value = _tracks.value.map { track ->
            if (track.id == trackId) track.copy(layers = track.layers + newLayer) else track
        }
        selectLayer(newLayer.id)
    }

    // -------------------------------------------
    // Merge / split / delete
    // -------------------------------------------
    /** Merges every currently multi-selected layer (within the same track) into one clip
     *  spanning their combined start/end range. Keyframes from the earliest-starting
     *  layer are kept; this mirrors a simple "merge clips" NLE operation. */
    fun mergeSelectedLayers() {
        val ids = _selectedLayerIds.value
        if (ids.size < 2) return

        _tracks.value = _tracks.value.map { track ->
            val toMerge = track.layers.filter { it.id in ids }
            if (toMerge.size < 2) return@map track

            val earliest = toMerge.minByOrNull { it.startTimeMs }!!
            val minStart = toMerge.minOf { it.startTimeMs }
            val maxEnd = toMerge.maxOf { it.startTimeMs + it.durationMs }
            val mergedName = "Merged (" + toMerge.joinToString(" + ") { it.name } + ")"

            val merged = earliest.copy(
                id = "merged_${System.currentTimeMillis()}",
                name = mergedName,
                startTimeMs = minStart,
                durationMs = maxEnd - minStart
            )

            track.copy(layers = track.layers.filterNot { it.id in ids } + merged)
        }
        _selectedLayerIds.value = emptySet()
        _selectedLayerId.value = null
    }

    /** Splits the layer under the playhead into two independent clips at the current time. */
    fun splitLayerAtPlayhead(layerId: String) {
        val splitTime = _currentPositionMs.value
        _tracks.value = _tracks.value.map { track ->
            val layer = track.layers.find { it.id == layerId } ?: return@map track
            val end = layer.startTimeMs + layer.durationMs
            if (splitTime <= layer.startTimeMs || splitTime >= end) return@map track

            val firstHalf = layer.copy(
                id = "${layer.id}_a",
                durationMs = splitTime - layer.startTimeMs
            )
            val secondHalf = layer.copy(
                id = "${layer.id}_b",
                name = "${layer.name} (2)",
                startTimeMs = splitTime,
                durationMs = end - splitTime
            )
            track.copy(layers = track.layers.filterNot { it.id == layerId } + firstHalf + secondHalf)
        }
    }

    fun deleteSelectedLayers() {
        val ids = _selectedLayerIds.value
        if (ids.isEmpty()) return
        _tracks.value = _tracks.value.map { track ->
            track.copy(layers = track.layers.filterNot { it.id in ids })
        }
        _selectedLayerIds.value = emptySet()
        _selectedLayerId.value = null
    }

    // -------------------------------------------
    // Keyframe editing (used by both the timeline's draggable diamonds
    // and the advanced Graph Editor curve view).
    // -------------------------------------------
    fun updateKeyframeTime(layerId: String, channelKey: String, keyframeId: String, newTimeMs: Long) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(layers = track.layers.map { layer ->
                if (layer.id != layerId) return@map layer
                val channel = layer.transform.getChannel(channelKey)
                val updatedKfs = channel.keyframes.map { kf ->
                    if (kf.id == keyframeId) kf.copy(timeMs = newTimeMs.coerceAtLeast(0L)) else kf
                }.sortedBy { it.timeMs }
                layer.copy(transform = layer.transform.withChannel(channelKey, channel.copy(keyframes = updatedKfs)))
            })
        }
    }

    fun updateKeyframeValue(layerId: String, channelKey: String, keyframeId: String, newValue: Float) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(layers = track.layers.map { layer ->
                if (layer.id != layerId) return@map layer
                val channel = layer.transform.getChannel(channelKey)
                val updatedKfs = channel.keyframes.map { kf ->
                    if (kf.id == keyframeId) kf.copy(value = newValue) else kf
                }
                layer.copy(transform = layer.transform.withChannel(channelKey, channel.copy(keyframes = updatedKfs)))
            })
        }
    }

    fun updateKeyframeHandle(layerId: String, channelKey: String, keyframeId: String, isEaseOut: Boolean, newHandle: BezierHandle) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(layers = track.layers.map { layer ->
                if (layer.id != layerId) return@map layer
                val channel = layer.transform.getChannel(channelKey)
                val updatedKfs = channel.keyframes.map { kf ->
                    if (kf.id != keyframeId) kf
                    else if (isEaseOut) kf.copy(easeOut = newHandle) else kf.copy(easeIn = newHandle)
                }
                layer.copy(transform = layer.transform.withChannel(channelKey, channel.copy(keyframes = updatedKfs)))
            })
        }
    }

    fun addKeyframeAtPlayhead(layerId: String, channelKey: String) {
        val time = _currentPositionMs.value
        _tracks.value = _tracks.value.map { track ->
            track.copy(layers = track.layers.map { layer ->
                if (layer.id != layerId) return@map layer
                val channel = layer.transform.getChannel(channelKey)
                val currentValue = AnimationMathEngine.evaluateFloatChannel(channel, time)
                val newKf = Keyframe(
                    id = "kf_${System.currentTimeMillis()}",
                    timeMs = time,
                    value = currentValue,
                    easeIn = BezierHandle(0.33f, 0.33f),
                    easeOut = BezierHandle(0.66f, 0.66f),
                    interpolation = InterpolationType.BEZIER
                )
                val updated = (channel.keyframes + newKf).sortedBy { it.timeMs }
                layer.copy(transform = layer.transform.withChannel(channelKey, channel.copy(keyframes = updated)))
            })
        }
    }
}

// ==========================================
// SECTION 5: MAIN ACTIVITY & UI APP SHELL
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionStudioApp()
        }
    }
}

@Composable
fun MotionStudioApp(viewModel: CompositionViewModel = viewModel()) {
    val activePanel by viewModel.activePanel.collectAsState()
    val compositionName by viewModel.compositionName.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MotionTheme.Obsidian
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Enterprise Top Application Bar
            TopAppBar(
                compositionName = compositionName,
                activePanel = activePanel,
                onPanelChange = { viewModel.setActivePanel(it) }
            )

            // Dynamic Central Viewport / Workspace Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MotionTheme.SurfaceDark)
            ) {
                when (activePanel) {
                    "timeline" -> PreviewViewport(viewModel)
                    "graph" -> GraphEditorCanvas(viewModel)
                    "color" -> ColorGradingPanel(viewModel)
                    "effects" -> EffectsControlPanel(viewModel)
                    "inspector" -> PropertyInspectorPanel(viewModel)
                }
            }

            // Lower Multi-Track Timeline & Scrubbing Controller Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .background(MotionTheme.Obsidian)
                    .border(width = 1.dp, color = MotionTheme.BorderColor)
            ) {
                TimelineHeaderControls(viewModel)
                TimelineVirtualizedCanvas(viewModel)
            }

            // Bottom action toolbar: null objects, merge, split, delete.
            BottomActionToolbar(viewModel)
        }
    }
}

@Composable
fun TopAppBar(compositionName: String, activePanel: String, onPanelChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MotionTheme.SurfaceDark)
            .border(width = 1.dp, color = MotionTheme.BorderColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MotionTheme.ElectricBlue),
                contentAlignment = Alignment.Center
            ) {
                Text("MS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column {
                Text("Motion Studio Enterprise", color = MotionTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(compositionName, color = MotionTheme.TextSecondary, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelTabButton("Timeline", activePanel == "timeline") { onPanelChange("timeline") }
            PanelTabButton("Graph", activePanel == "graph") { onPanelChange("graph") }
            PanelTabButton("Color", activePanel == "color") { onPanelChange("color") }
            PanelTabButton("Effects", activePanel == "effects") { onPanelChange("effects") }
            PanelTabButton("Inspector", activePanel == "inspector") { onPanelChange("inspector") }
        }
    }
}

@Composable
fun PanelTabButton(title: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) MotionTheme.ElectricBlue.copy(alpha = 0.25f) else Color.Transparent)
            .border(1.dp, if (isActive) MotionTheme.ElectricBlue else MotionTheme.BorderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, color = if (isActive) MotionTheme.ElectricBlue else MotionTheme.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// SECTION 6: PREVIEW VIEWPORT COMPONENT
// ==========================================
@Composable
fun PreviewViewport(viewModel: CompositionViewModel) {
    val currentPosition by viewModel.currentPositionMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val duration by viewModel.durationMs.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .height(292.dp)
                    .background(Color.Black)
                    .border(2.dp, MotionTheme.ElectricBlue, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎬 GPU Accelerated Viewport", color = MotionTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Timecode: ${formatTimeCode(currentPosition)} / ${formatTimeCode(duration)}", color = MotionTheme.ElectricBlue, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Status: ${if (isPlaying) "PLAYING (60 FPS)" else "PAUSED"}", color = MotionTheme.TextSecondary, fontSize = 11.sp)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.setCurrentPosition(0L) },
                    colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.SurfaceElevated)
                ) {
                    Text("⏮ Start", fontSize = 11.sp, color = MotionTheme.TextPrimary)
                }

                Button(
                    onClick = { viewModel.setIsPlaying(!isPlaying) },
                    colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.ElectricBlue)
                ) {
                    Text(if (isPlaying) "⏸ Pause Playback" else "▶ Play Composition", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.setCurrentPosition(duration) },
                    colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.SurfaceElevated)
                ) {
                    Text("End ⏭", fontSize = 11.sp, color = MotionTheme.TextPrimary)
                }
            }
        }
    }
}

fun formatTimeCode(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val millis = (ms % 1000) / 10
    return String.format("%02d:%02d:%02d", mins, secs, millis)
}

// ==========================================
// SECTION 7: ADVANCED GRAPH / CURVE EDITOR
// ==========================================
// A hit-testable point on screen, computed each draw pass so pointerInput
// can find the nearest keyframe/handle without per-point gesture detectors.
private sealed class CurvePoint {
    abstract val screenX: Float
    abstract val screenY: Float

    data class KeyframePoint(
        val keyframeId: String,
        val timeMs: Long,
        val value: Float,
        override val screenX: Float,
        override val screenY: Float
    ) : CurvePoint()

    data class HandlePoint(
        val keyframeId: String,
        val isEaseOut: Boolean,
        val handle: BezierHandle,
        override val screenX: Float,
        override val screenY: Float
    ) : CurvePoint()
}

@Composable
fun GraphEditorCanvas(viewModel: CompositionViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val currentPosition by viewModel.currentPositionMs.collectAsState()

    val layer = remember(tracks, selectedLayerId) {
        tracks.flatMap { it.layers }.find { it.id == selectedLayerId }
    }

    var horizontalZoom by remember { mutableStateOf(1f) }
    val hitTestPoints = remember { mutableStateOf(listOf<CurvePoint>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MotionTheme.SurfaceElevated)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Curve Editor — Newton-Raphson Bézier Solver",
                color = MotionTheme.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { horizontalZoom = (horizontalZoom * 0.8f).coerceAtLeast(0.25f) },
                    colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.SurfaceHighlight),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("−", color = MotionTheme.TextPrimary, fontSize = 12.sp) }
                Button(
                    onClick = { horizontalZoom = (horizontalZoom * 1.25f).coerceAtMost(6f) },
                    colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.SurfaceHighlight),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("+", color = MotionTheme.TextPrimary, fontSize = 12.sp) }
                if (layer != null) {
                    Button(
                        onClick = { viewModel.addKeyframeAtPlayhead(layer.id, selectedChannel) },
                        colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.ElectricBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("◆ Add Keyframe", color = Color.White, fontSize = 11.sp) }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TRANSFORM_CHANNEL_KEYS.forEach { key ->
                ChannelTabButton(
                    label = key.replaceFirstChar { it.uppercase() },
                    isActive = selectedChannel == key,
                    onClick = { viewModel.setSelectedChannel(key) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (layer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a layer to edit its animation curve", color = MotionTheme.TextSecondary, fontSize = 12.sp)
            }
            return@Column
        }

        val channel = layer.transform.getChannel(selectedChannel)
        val allValues = channel.keyframes.map { it.value }
        val valueMin = (allValues.minOrNull() ?: 0f).let { min(it, 0f) }
        val valueMax = (allValues.maxOrNull() ?: 1f).let { max(it, valueMin + 1f) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MotionTheme.SurfaceDark)
                .border(1.dp, MotionTheme.BorderColor, RoundedCornerShape(4.dp))
                .pointerInput(layer.id, selectedChannel, horizontalZoom, duration, valueMin, valueMax) {
                    var activePoint: CurvePoint? = null
                    var currentTimeAtDrag = 0L
                    var currentValueAtDrag = 0f

                    detectDragGestures(
                        onDragStart = { pos ->
                            activePoint = hitTestPoints.value.minByOrNull { pt ->
                                val dx = pt.screenX - pos.x
                                val dy = pt.screenY - pos.y
                                dx * dx + dy * dy
                            }?.takeIf { pt ->
                                val dx = pt.screenX - pos.x
                                val dy = pt.screenY - pos.y
                                (dx * dx + dy * dy) < 900f // ~30px hit radius
                            }
                            when (val pt = activePoint) {
                                is CurvePoint.KeyframePoint -> {
                                    currentTimeAtDrag = pt.timeMs
                                    currentValueAtDrag = pt.value
                                }
                                else -> {}
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val pxPerMs = (size.width * horizontalZoom) / duration.toFloat().coerceAtLeast(1f)
                            val pxPerValue = size.height / (valueMax - valueMin).coerceAtLeast(0.001f)

                            when (val pt = activePoint) {
                                is CurvePoint.KeyframePoint -> {
                                    currentTimeAtDrag = (currentTimeAtDrag + (dragAmount.x / pxPerMs).toLong())
                                        .coerceIn(0L, duration)
                                    currentValueAtDrag -= dragAmount.y / pxPerValue
                                    viewModel.updateKeyframeTime(layer.id, selectedChannel, pt.keyframeId, currentTimeAtDrag)
                                    viewModel.updateKeyframeValue(layer.id, selectedChannel, pt.keyframeId, currentValueAtDrag)
                                }
                                is CurvePoint.HandlePoint -> {
                                    val newX = (pt.handle.x + dragAmount.x / 120f).coerceIn(0f, 1f)
                                    val newY = (pt.handle.y - dragAmount.y / 120f).coerceIn(0f, 1f)
                                    val newHandle = BezierHandle(newX, newY)
                                    viewModel.updateKeyframeHandle(layer.id, selectedChannel, pt.keyframeId, pt.isEaseOut, newHandle)
                                    activePoint = pt.copy(handle = newHandle)
                                }
                                null -> {}
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width * horizontalZoom
                val h = size.height
                val pxPerMs = w / duration.toFloat().coerceAtLeast(1f)
                val pxPerValue = h / (valueMax - valueMin).coerceAtLeast(0.001f)

                fun timeToX(t: Long) = t * pxPerMs
                fun valueToY(v: Float) = h - (v - valueMin) * pxPerValue

                // Grid
                val gridStepMs = 1000L
                var gt = 0L
                while (gt <= duration) {
                    drawLine(MotionTheme.BorderColor, Offset(timeToX(gt), 0f), Offset(timeToX(gt), h), 0.5f)
                    gt += gridStepMs
                }
                val gridRows = 6
                for (i in 0..gridRows) {
                    val gy = h * i / gridRows
                    drawLine(MotionTheme.BorderColor, Offset(0f, gy), Offset(w, gy), 0.5f)
                }

                // Playhead
                drawLine(
                    MotionTheme.AccentAmber,
                    Offset(timeToX(currentPosition), 0f),
                    Offset(timeToX(currentPosition), h),
                    2f
                )

                val newPoints = mutableListOf<CurvePoint>()
                val kfs = channel.keyframes

                if (kfs.isNotEmpty()) {
                    // Sampled real curve using the actual bezier solver.
                    val path = Path()
                    val sampleStepMs = max(20L, duration / 400)
                    var t = 0L
                    var first = true
                    while (t <= duration) {
                        val v = AnimationMathEngine.evaluateFloatChannel(channel, t)
                        val x = timeToX(t)
                        val y = valueToY(v)
                        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                        t += sampleStepMs
                    }
                    drawPath(path, MotionTheme.NeonViolet, style = Stroke(width = 3f))

                    // Keyframes + ease handles
                    kfs.forEachIndexed { idx, kf ->
                        val kx = timeToX(kf.timeMs)
                        val ky = valueToY(kf.value)

                        if (kf.interpolation == InterpolationType.BEZIER) {
                            val nextKf = kfs.getOrNull(idx + 1)
                            // Ease-out handle (this keyframe -> next)
                            if (nextKf != null) {
                                val hx = kx + (timeToX(nextKf.timeMs) - kx) * kf.easeOut.x
                                val hy = ky + (valueToY(nextKf.value) - ky) * (1f - kf.easeOut.y)
                                drawLine(MotionTheme.AccentGreen, Offset(kx, ky), Offset(hx, hy), 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                                drawCircle(MotionTheme.AccentGreen, radius = 6f, center = Offset(hx, hy))
                                newPoints += CurvePoint.HandlePoint(kf.id, true, kf.easeOut, hx, hy)
                            }
                            // Ease-in handle (previous -> this keyframe)
                            val prevKf = kfs.getOrNull(idx - 1)
                            if (prevKf != null) {
                                val hx = kx - (kx - timeToX(prevKf.timeMs)) * kf.easeIn.x
                                val hy = ky - (valueToY(prevKf.value) - ky) * (1f - kf.easeIn.y)
                                drawLine(MotionTheme.AccentGreen, Offset(kx, ky), Offset(hx, hy), 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                                drawCircle(MotionTheme.AccentGreen, radius = 6f, center = Offset(hx, hy))
                                newPoints += CurvePoint.HandlePoint(kf.id, false, kf.easeIn, hx, hy)
                            }
                        }

                        drawCircle(MotionTheme.ElectricBlue, radius = 8f, center = Offset(kx, ky))
                        drawCircle(MotionTheme.TextPrimary, radius = 3f, center = Offset(kx, ky))
                        newPoints += CurvePoint.KeyframePoint(kf.id, kf.timeMs, kf.value, kx, ky)
                    }
                } else {
                    drawIntoCanvas {
                        // No keyframes yet on this channel.
                    }
                }

                hitTestPoints.value = newPoints
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Drag blue diamonds to retime/rescale a keyframe · drag green handles to shape easing · amber line = playhead",
            color = MotionTheme.TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun ChannelTabButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) MotionTheme.NeonViolet.copy(alpha = 0.25f) else Color.Transparent)
            .border(1.dp, if (isActive) MotionTheme.NeonViolet else MotionTheme.BorderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (isActive) MotionTheme.NeonViolet else MotionTheme.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// SECTION 8: PROFESSIONAL COLOR GRADING SUITE
// ==========================================
@Composable
fun ColorGradingPanel(viewModel: CompositionViewModel) {
    val grading by viewModel.colorGrading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Professional Color Wheels & Primary Correction", color = MotionTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ColorWheelNode("Lift (Shadows)", grading.liftR) { r -> viewModel.updateColorGrading { it.copy(liftR = r) } }
            ColorWheelNode("Gamma (Midtones)", grading.gammaR) { r -> viewModel.updateColorGrading { it.copy(gammaR = r) } }
            ColorWheelNode("Gain (Highlights)", grading.gainR) { r -> viewModel.updateColorGrading { it.copy(gainR = r) } }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { viewModel.updateColorGrading { ColorGradingState() } }, colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.SurfaceElevated)) {
                Text("Reset Color Balance", color = MotionTheme.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ColorWheelNode(label: String, valR: Float, onValChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(MotionTheme.SurfaceDark)
                .border(2.dp, MotionTheme.ElectricBlue, RoundedCornerShape(50.dp))
                .clickable { onValChange((valR + 0.1f) % 1.0f) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MotionTheme.NeonViolet)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = MotionTheme.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// SECTION 9: SHADER EFFECTS & PROPERTY INSPECTOR
// ==========================================
@Composable
fun EffectsControlPanel(viewModel: CompositionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AGSL GPU Shader Effects & Filters", color = MotionTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(16.dp))
        EffectToggleCard("Chromatic Aberration & Glitch", true) {}
        EffectToggleCard("Whip Pan Motion Blur", true) {}
        EffectToggleCard("Cinematic Film Grain & Halation", false) {}
    }
}

@Composable
fun EffectToggleCard(title: String, initialActive: Boolean, onToggle: (Boolean) -> Unit) {
    var active by remember { mutableStateOf(initialActive) }
    Row(
        modifier = Modifier
            .width(360.dp)
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MotionTheme.SurfaceElevated)
            .border(1.dp, MotionTheme.BorderColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = MotionTheme.TextPrimary, fontSize = 12.sp)
        Switch(checked = active, onCheckedChange = { active = it; onToggle(it) })
    }
}

@Composable
fun PropertyInspectorPanel(viewModel: CompositionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Transform & Keyframe Property Inspector", color = MotionTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        InspectorPropertyRow("Position X", "0.0 px")
        InspectorPropertyRow("Position Y", "0.0 px")
        InspectorPropertyRow("Scale Uniform", "100.0 %")
        InspectorPropertyRow("Rotation Angle", "0.0 °")
        InspectorPropertyRow("Opacity Master", "100.0 %")
    }
}

@Composable
fun InspectorPropertyRow(propName: String, propVal: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MotionTheme.SurfaceElevated)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(propName, color = MotionTheme.TextSecondary, fontSize = 12.sp)
        Text(propVal, color = MotionTheme.ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// ==========================================
// SECTION 10: MULTI-TRACK TIMELINE & CANVAS
// ==========================================
@Composable
fun TimelineHeaderControls(viewModel: CompositionViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val zoomScale by viewModel.zoomScale.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MotionTheme.SurfaceDark)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.setIsPlaying(!isPlaying) },
                colors = ButtonDefaults.buttonColors(containerColor = MotionTheme.ElectricBlue),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (isPlaying) "Pause" else "Play", fontSize = 11.sp, color = Color.White)
            }
            Text("Snapping: Enabled (±5ms)", color = MotionTheme.TextSecondary, fontSize = 10.sp)
        }
        Text("Zoom: ${"%.1f".format(zoomScale)} px/ms", color = MotionTheme.TextSecondary, fontSize = 10.sp)
    }
}

@Composable
fun TimelineVirtualizedCanvas(viewModel: CompositionViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val zoomScale by viewModel.zoomScale.collectAsState()
    val selectedLayerIds by viewModel.selectedLayerIds.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MotionTheme.Obsidian)
    ) {
        items(tracks) { track ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .border(width = 0.5.dp, color = MotionTheme.BorderColor)
                    .background(MotionTheme.SurfaceDark)
                    .padding(6.dp)
            ) {
                Text(track.name, color = MotionTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxSize()) {
                    track.layers.forEach { layer ->
                        val isSelected = layer.id in selectedLayerIds
                        val isNull = layer.type == "null"
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(220.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isNull) MotionTheme.AccentAmber.copy(alpha = 0.15f)
                                    else MotionTheme.ElectricBlue.copy(alpha = 0.35f)
                                )
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) MotionTheme.AccentGreen
                                    else if (isNull) MotionTheme.AccentAmber else MotionTheme.ElectricBlue,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    viewModel.toggleLayerSelection(layer.id)
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    (if (isNull) "⊕ " else "") + layer.name,
                                    color = MotionTheme.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "◆ Keyframes Active: ${layer.transform.scale.keyframes.size + layer.transform.positionX.keyframes.size}",
                                    color = MotionTheme.NeonViolet,
                                    fontSize = 9.sp
                                )
                            }

                            // Draggable keyframe diamonds along the bottom edge of the clip,
                            // positioned by time and retimed by dragging horizontally.
                            DraggableKeyframeStrip(
                                layer = layer,
                                zoomScale = zoomScale,
                                onRetime = { channelKey, kfId, newTime ->
                                    viewModel.updateKeyframeTime(layer.id, channelKey, kfId, newTime)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Renders every keyframe across a layer's transform channels as a small diamond
 *  positioned by time, draggable left/right to retime it (clamped to the clip). */
@Composable
private fun BoxScope.DraggableKeyframeStrip(
    layer: Layer,
    zoomScale: Float,
    onRetime: (channelKey: String, keyframeId: String, newTimeMs: Long) -> Unit
) {
    val pxPerMs = zoomScale
    val allMarkers = remember(layer) {
        TRANSFORM_CHANNEL_KEYS.flatMap { key ->
            layer.transform.getChannel(key).keyframes.map { kf -> Triple(key, kf.id, kf.timeMs) }
        }
    }

    allMarkers.forEach { (channelKey, kfId, timeMs) ->
        var localTimeMs by remember(kfId, timeMs) { mutableStateOf(timeMs) }
        val relativeMs = (localTimeMs - layer.startTimeMs).coerceAtLeast(0L)
        val offsetPx = (relativeMs * pxPerMs).dp

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = offsetPx, y = (-4).dp)
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MotionTheme.NeonViolet)
                .border(1.dp, MotionTheme.TextPrimary, RoundedCornerShape(2.dp))
                .pointerInput(kfId) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val deltaMs = (drag.x / pxPerMs).toLong()
                        localTimeMs = (localTimeMs + deltaMs).coerceIn(
                            layer.startTimeMs,
                            layer.startTimeMs + layer.durationMs
                        )
                        onRetime(channelKey, kfId, localTimeMs)
                    }
                }
        )
    }
}

// ==========================================
// SECTION 11: BOTTOM ACTION TOOLBAR
// ==========================================
@Composable
fun BottomActionToolbar(viewModel: CompositionViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val selectedLayerIds by viewModel.selectedLayerIds.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()

    val firstVideoTrackId = remember(tracks) { tracks.firstOrNull()?.id }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MotionTheme.SurfaceDark)
            .border(width = 1.dp, color = MotionTheme.BorderColor)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ToolbarActionButton(
            label = "⊕ Null",
            enabled = firstVideoTrackId != null,
            containerColor = MotionTheme.AccentAmber
        ) {
            firstVideoTrackId?.let { viewModel.addNullLayer(it) }
        }

        ToolbarActionButton(
            label = "⧉ Merge Clips (${selectedLayerIds.size})",
            enabled = selectedLayerIds.size >= 2,
            containerColor = MotionTheme.ElectricBlue
        ) {
            viewModel.mergeSelectedLayers()
        }

        ToolbarActionButton(
            label = "✂ Split at Playhead",
            enabled = selectedLayerId != null,
            containerColor = MotionTheme.SurfaceHighlight
        ) {
            selectedLayerId?.let { viewModel.splitLayerAtPlayhead(it) }
        }

        ToolbarActionButton(
            label = "🗑 Delete",
            enabled = selectedLayerIds.isNotEmpty(),
            containerColor = MotionTheme.AccentRed
        ) {
            viewModel.deleteSelectedLayers()
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Tap a clip to select · tap more to multi-select for merging",
            color = MotionTheme.TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun ToolbarActionButton(label: String, enabled: Boolean, containerColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = MotionTheme.SurfaceElevated
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 11.sp, color = if (enabled) Color.White else MotionTheme.TextSecondary, fontWeight = FontWeight.Medium)
    }
}

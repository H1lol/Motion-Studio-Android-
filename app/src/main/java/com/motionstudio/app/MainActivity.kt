package com.motionstudio.animeeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// 1. DESIGN SYSTEM & COLOR PALETTE
// ============================================================================
object StudioTheme {
    val DarkBg = Color(0xFF090A0F)
    val SurfaceCard = Color(0xFF12141D)
    val SurfaceElevated = Color(0xFF1A1D2B)
    val SurfaceBorder = Color(0xFF272B3F)
    val AccentIndigo = Color(0xFF6366F1)
    val AccentPurple = Color(0xFFA855F7)
    val AccentPink = Color(0xFFEC4899)
    val AccentTeal = Color(0xFF14B8A6)
    val AccentAmber = Color(0xFFF59E0B)
    val AccentRed = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val StudioGradient = Brush.horizontalGradient(
        colors = listOf(AccentIndigo, AccentPurple, AccentPink)
    )
}

// ============================================================================
// 2. DOMAIN MODELS & ENUMS
// ============================================================================
enum class StudioTab(val label: String, val iconName: String) {
    TIMELINE("Timeline", "timeline"),
    VELOCITY("Velocity Curve", "speed"),
    NODES("Node Compositor", "account_tree"),
    AI_ASSIST("AI Copilot", "auto_awesome"),
    VAULT("Media Vault", "folder")
}

enum class MediaType { VIDEO, AUDIO, IMAGE, LUT, PRESET, SHADER }
enum class TrackType { VIDEO, OVERLAY, EFFECT, AUDIO }
enum class TimelineTool { SELECT, RAZOR, TRIM, KEYFRAME, RIPPLE }
enum class AspectRatioOption(val label: String, val ratio: Float) {
    VERTICAL_9_16("9:16 TikTok/Reels", 9f / 16f),
    WIDESCREEN_16_9("16:9 AMV/YouTube", 16f / 9f),
    SQUARE_1_1("1:1 Feed", 1f)
}

data class MediaAsset(
    val id: String,
    val title: String,
    val durationText: String,
    val resolution: String,
    val fps: Int,
    val sizeText: String,
    val type: MediaType,
    val colorAccent: Color
)

data class TrackClip(
    val id: String,
    val name: String,
    val trackId: String,
    val startFrame: Int,
    val durationFrames: Int,
    val color: Color,
    val speedRampPreset: String = "Linear",
    val speedScale: Float = 1.0f,
    val blendMode: String = "Normal",
    val opacity: Float = 1.0f,
    val keyframesCount: Int = 0
)

data class TrackLayer(
    val id: String,
    val name: String,
    val type: TrackType,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val isVisible: Boolean = true,
    val clips: List<TrackClip>
)

data class VelocityPoint(
    var x: Float, // Normalized 0.0 .. 1.0 (Clip time)
    var y: Float  // Speed scale factor 0.1 .. 5.0
)

data class NodeItem(
    val id: String,
    val title: String,
    val category: String,
    var position: Offset,
    val color: Color,
    val inputs: List<String>,
    val outputs: List<String>,
    val parameters: Map<String, String> = emptyMap()
)

data class AICopilotMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestampText: String,
    val isActionable: Boolean = false,
    val actionLabel: String = ""
)

data class ExportConfig(
    val resolution: String = "4K Ultra HD (2160p)",
    val fps: Int = 60,
    val format: String = "MP4 / H.264",
    val bitrateMbps: Int = 35,
    val enableTwixtor: Boolean = true
)

// ============================================================================
// 3. APPLICATION VIEWMODEL & STATE ENGINE
// ============================================================================
class StudioViewModel {
    private val _activeTab = MutableStateFlow(StudioTab.TIMELINE)
    val activeTab: StateFlow<StudioTab> = _activeTab.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(4200L) // 00:04.20
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>("clip_1")
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<String?>("n3")
    val selectedNodeId: StateFlow<String?> = _selectedNodeId.asStateFlow()

    private val _activeTool = MutableStateFlow(TimelineTool.SELECT)
    val activeTool: StateFlow<TimelineTool> = _activeTool.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioOption.VERTICAL_9_16)
    val aspectRatio: StateFlow<AspectRatioOption> = _aspectRatio.asStateFlow()

    private val _isExportModalOpen = MutableStateFlow(false)
    val isExportModalOpen: StateFlow<Boolean> = _isExportModalOpen.asStateFlow()

    private val _exportConfig = MutableStateFlow(ExportConfig())
    val exportConfig: StateFlow<ExportConfig> = _exportConfig.asStateFlow()

    // Multi-Track Timeline State
    private val _tracks = MutableStateFlow(
        listOf(
            TrackLayer("t1", "V1 • Main Anime Base", TrackType.VIDEO, false, false, true, listOf(
                TrackClip("clip_1", "Jujutsu_Fight_4K.mp4", "t1", 0, 120, StudioTheme.AccentIndigo, "Flash Ramp", 1.8f, opacity = 1.0f, keyframesCount = 4),
                TrackClip("clip_2", "Gojo_Domain_Expansion.mp4", "t1", 125, 90, StudioTheme.AccentPurple, "Hero Entrance", 1.0f, opacity = 1.0f, keyframesCount = 2)
            )),
            TrackLayer("t2", "V2 • VFX Particles & Glitch", TrackType.OVERLAY, false, false, true, listOf(
                TrackClip("clip_3", "Light_Leaks_Particle.mp4", "t2", 20, 150, StudioTheme.AccentPink, "Smooth", 1.0f, blendMode = "Screen", opacity = 0.85f, keyframesCount = 1)
            )),
            TrackLayer("t3", "FX • Sapphire Glow & CC", TrackType.EFFECT, false, false, true, listOf(
                TrackClip("clip_5", "Neon_Glow_Adjustment.msfx", "t3", 0, 240, StudioTheme.AccentAmber, "Linear", 1.0f, keyframesCount = 6)
            )),
            TrackLayer("t4", "A1 • Phonk Remix Track", TrackType.AUDIO, false, false, true, listOf(
                TrackClip("clip_4", "Metamorphosis_Remix_Bass.wav", "t4", 0, 240, StudioTheme.AccentTeal, "Linear", 1.0f, keyframesCount = 0)
            ))
        )
    )
    val tracks: StateFlow<List<TrackLayer>> = _tracks.asStateFlow()

    // Interactive Velocity Curve Graph
    private val _velocityPoints = MutableStateFlow(
        mutableStateListOf(
            VelocityPoint(0.0f, 1.0f),
            VelocityPoint(0.20f, 0.2f),
            VelocityPoint(0.45f, 4.8f),
            VelocityPoint(0.70f, 0.3f),
            VelocityPoint(1.0f, 1.0f)
        )
    )
    val velocityPoints: StateFlow<List<VelocityPoint>> = _velocityPoints.asStateFlow()

    // Node Compositor Node Network
    private val _nodes = MutableStateFlow(
        listOf(
            NodeItem("n1", "Anime Source", "Input Media", Offset(40f, 100f), StudioTheme.AccentIndigo, emptyList(), listOf("RGBA"), mapOf("Resolution" to "4K", "ColorSpace" to "Rec.709")),
            NodeItem("n2", "Primary CC", "Color Wheel", Offset(260f, 60f), StudioTheme.AccentTeal, listOf("RGBA"), listOf("Out"), mapOf("Gain" to "1.25", "Lift" to "-0.05", "Sat" to "1.40")),
            NodeItem("n3", "Sapphire Glow", "FX Shader", Offset(480f, 160f), StudioTheme.AccentPurple, listOf("In"), listOf("RGB"), mapOf("Threshold" to "0.65", "Radius" to "45px", "Intensity" to "2.1")),
            NodeItem("n4", "RSMB Motion Blur", "Temporal Pass", Offset(700f, 80f), StudioTheme.AccentAmber, listOf("RGB"), listOf("Pass"), mapOf("Vector Sensitivity" to "0.85", "Blur Amount" to "1.5")),
            NodeItem("n5", "Master Render", "Output Engine", Offset(920f, 110f), StudioTheme.AccentPink, listOf("Final"), emptyList(), mapOf("Codec" to "ProRes 422", "FPS" to "60"))
        )
    )
    val nodes: StateFlow<List<NodeItem>> = _nodes.asStateFlow()

    // AI Copilot Chat Messages State
    private val _aiMessages = MutableStateFlow(
        listOf(
            AICopilotMessage("msg1", "Copilot", "Welcome to Motion Studio! I analyzed 'Jujutsu_Fight_4K.mp4'. Detected 4 sharp audio beat drops.", "10:42 AM"),
            AICopilotMessage("msg2", "Copilot", "Would you like me to auto-generate a 5-point Velocity Flash curve and snap cuts to beat markers?", "10:43 AM", true, "Apply Velocity Flash & Snap")
        )
    )
    val aiMessages: StateFlow<List<AICopilotMessage>> = _aiMessages.asStateFlow()

    // Project Media Vault Library
    private val _importedFiles = MutableStateFlow(
        listOf(
            MediaAsset("m1", "DemonSlayer_Climax_Fight.mp4", "00:14", "4K", 60, "142 MB", MediaType.VIDEO, StudioTheme.AccentIndigo),
            MediaAsset("m2", "Cyberpunk_Edgerunners_Speed.mp4", "00:08", "1080P", 120, "68 MB", MediaType.VIDEO, StudioTheme.AccentPurple),
            MediaAsset("m3", "Metamorphosis_Remix_Bass.wav", "02:45", "320 kbps", 44, "12 MB", MediaType.AUDIO, StudioTheme.AccentTeal),
            MediaAsset("m4", "Vibrant_Anime_Tone.cube", "3D LUT", "64x64", 0, "2.4 MB", MediaType.LUT, StudioTheme.AccentAmber),
            MediaAsset("m5", "Shake_Y_Impact_Bounce.msfx", "Preset", "Motion Vector", 0, "450 KB", MediaType.PRESET, StudioTheme.AccentPink),
            MediaAsset("m6", "Anime_Edge_Glow_Shader.glsl", "GPU Shader", "Vulkan/WebGL", 0, "12 KB", MediaType.SHADER, StudioTheme.AccentIndigo)
        )
    )
    val importedFiles: StateFlow<List<MediaAsset>> = _importedFiles.asStateFlow()

    // Action Methods
    fun setActiveTab(tab: StudioTab) { _activeTab.value = tab }
    fun togglePlay() { _isPlaying.value = !_isPlaying.value }
    fun selectClip(clipId: String) { _selectedClipId.value = clipId }
    fun selectNode(nodeId: String) { _selectedNodeId.value = nodeId }
    fun setActiveTool(tool: TimelineTool) { _activeTool.value = tool }
    fun setAspectRatio(option: AspectRatioOption) { _aspectRatio.value = option }
    fun setExportModalOpen(open: Boolean) { _isExportModalOpen.value = open }

    fun stepFrame(deltaFrames: Int) {
        val frameMs = (1000f / _fps.value).toLong()
        _currentTimeMs.value = (_currentTimeMs.value + (deltaFrames * frameMs)).coerceAtLeast(0L)
    }

    fun applyVelocityPreset(presetName: String) {
        when (presetName) {
            "Flash" -> {
                _velocityPoints.value = mutableStateListOf(
                    VelocityPoint(0.0f, 1.0f), VelocityPoint(0.15f, 0.15f),
                    VelocityPoint(0.40f, 4.9f), VelocityPoint(0.65f, 0.25f), VelocityPoint(1.0f, 1.0f)
                )
            }
            "Hero" -> {
                _velocityPoints.value = mutableStateListOf(
                    VelocityPoint(0.0f, 0.3f), VelocityPoint(0.30f, 0.5f),
                    VelocityPoint(0.50f, 3.8f), VelocityPoint(0.80f, 1.2f), VelocityPoint(1.0f, 1.0f)
                )
            }
            "Montage" -> {
                _velocityPoints.value = mutableStateListOf(
                    VelocityPoint(0.0f, 2.5f), VelocityPoint(0.25f, 0.4f),
                    VelocityPoint(0.50f, 3.2f), VelocityPoint(0.75f, 0.4f), VelocityPoint(1.0f, 2.5f)
                )
            }
            "Reset" -> {
                _velocityPoints.value = mutableStateListOf(
                    VelocityPoint(0.0f, 1.0f), VelocityPoint(0.50f, 1.0f), VelocityPoint(1.0f, 1.0f)
                )
            }
        }
    }

    fun addTrack(type: TrackType) {
        val newId = "t_${_tracks.value.size + 1}"
        val name = when (type) {
            TrackType.VIDEO -> "V${_tracks.value.size + 1} • Video Track"
            TrackType.OVERLAY -> "V${_tracks.value.size + 1} • VFX Overlay"
            TrackType.EFFECT -> "FX${_tracks.value.size + 1} • Adjustment Node"
            TrackType.AUDIO -> "A${_tracks.value.size + 1} • Audio Layer"
        }
        _tracks.value = _tracks.value + TrackLayer(newId, name, type, false, false, true, emptyList())
    }

    fun splitClipAtCurrentPlayhead() {
        val currentFrame = ((_currentTimeMs.value / 1000f) * _fps.value).toInt()
        val clipId = _selectedClipId.value ?: return
        val updatedTracks = _tracks.value.map { track ->
            val clipToSplit = track.clips.find { it.id == clipId }
            if (clipToSplit != null && currentFrame > clipToSplit.startFrame && currentFrame < (clipToSplit.startFrame + clipToSplit.durationFrames)) {
                val firstDuration = currentFrame - clipToSplit.startFrame
                val secondDuration = clipToSplit.durationFrames - firstDuration

                val part1 = clipToSplit.copy(durationFrames = firstDuration)
                val part2 = clipToSplit.copy(
                    id = "${clipToSplit.id}_split",
                    name = "${clipToSplit.name} (Part 2)",
                    startFrame = currentFrame,
                    durationFrames = secondDuration
                )

                val newClips = track.clips.flatMap { if (it.id == clipId) listOf(part1, part2) else listOf(it) }
                track.copy(clips = newClips)
            } else track
        }
        _tracks.value = updatedTracks
    }

    fun deleteSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.filterNot { it.id == clipId })
        }
        _selectedClipId.value = null
    }

    fun updateNodePosition(nodeId: String, newPos: Offset) {
        _nodes.value = _nodes.value.map { node ->
            if (node.id == nodeId) node.copy(position = newPos) else node
        }
    }

    fun sendAIPrompt(userQuery: String) {
        val userMsg = AICopilotMessage("u_${System.currentTimeMillis()}", "You", userQuery, "Just now")
        val aiReply = AICopilotMessage(
            "ai_${System.currentTimeMillis()}",
            "Copilot",
            "Analyzed prompt: '$userQuery'. Applied Sapphire S_Shake preset and optimized frame keyframes for 60fps Twixtor smoothness.",
            "Just now"
        )
        _aiMessages.value = _aiMessages.value + userMsg + aiReply
    }
}

// ============================================================================
// 4. MAIN ACTIVITY ENTRY POINT
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = StudioViewModel()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = StudioTheme.DarkBg,
                    surface = StudioTheme.SurfaceCard,
                    primary = StudioTheme.AccentIndigo
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioTheme.DarkBg
                ) {
                    MotionStudioApp(viewModel)
                }
            }
        }
    }
}

// ============================================================================
// 5. TOP APP BAR & PROJECT HEADER
// ============================================================================
@Composable
fun TopAppBarHeader(viewModel: StudioViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val fps by viewModel.fps.collectAsState()

    val currentFrames = remember(currentTimeMs, fps) {
        ((currentTimeMs / 1000f) * fps).toInt()
    }

    val formattedTime = remember(currentTimeMs) {
        val totalSecs = currentTimeMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val millis = (currentTimeMs % 1000) / 10
        String.format("%02d:%02d.%02d", mins, secs, millis)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(StudioTheme.SurfaceCard)
            .border(1.dp, StudioTheme.SurfaceBorder)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Identity & Project Metadata
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioTheme.StudioGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MOTION STUDIO",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = StudioTheme.TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = StudioTheme.AccentPink.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, StudioTheme.AccentPink.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "PRO AMV",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StudioTheme.AccentPink,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = "Project: Jujutsu_Velocity_Edit_v4.ms",
                    fontSize = 9.sp,
                    color = StudioTheme.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Playhead Frame & Timecode Readout
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = StudioTheme.DarkBg,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, StudioTheme.SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) StudioTheme.AccentTeal else StudioTheme.TextMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = StudioTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[$currentFrames F]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTheme.AccentIndigo
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // AI Copilot Launcher Button
            IconButton(
                onClick = { viewModel.setActiveTab(StudioTab.AI_ASSIST) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioTheme.AccentPurple.copy(alpha = 0.2f))
                    .border(1.dp, StudioTheme.AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "AI Copilot",
                    tint = StudioTheme.AccentPurple,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Render & Export Button
            Button(
                onClick = { viewModel.setExportModalOpen(true) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioTheme.AccentIndigo),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 6. VIDEO PREVIEW VIEWPORT & CANVAS ENGINE
// ============================================================================
@Composable
fun VideoPreviewPlayer(viewModel: StudioViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "video_canvas")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(StudioTheme.SurfaceCard)
            .border(1.dp, StudioTheme.SurfaceBorder),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic Aspect Ratio Bounding Preview Box
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio.ratio)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioTheme.DarkBg)
                .border(1.dp, StudioTheme.SurfaceBorder),
            contentAlignment = Alignment.Center
        ) {
            // Simulated Anime Frame Render Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Radial background simulating anime energy fight scene
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioTheme.AccentPurple.copy(alpha = pulseGlow * 0.45f),
                            StudioTheme.AccentIndigo.copy(alpha = 0.2f),
                            StudioTheme.DarkBg
                        ),
                        center = Offset(w * 0.5f, h * 0.45f),
                        radius = w * 0.7f
                    )
                )

                // Composition Rule of Thirds Grid Lines
                val gridColor = Color.White.copy(alpha = 0.08f)
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(w * 2 / 3f, 0f), Offset(w * 2 / 3f, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h * 2 / 3f), Offset(w, h * 2 / 3f), strokeWidth = 1f)

                // Velocity Speed Trail Visual Vectors
                if (isPlaying) {
                    drawCircle(
                        brush = StudioTheme.StudioGradient,
                        radius = w * 0.28f * pulseGlow,
                        center = Offset(w * 0.5f, h * 0.45f),
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Graphic Overlay Element
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(StudioTheme.StudioGradient)
                        .shadow(12.dp, CircleShape, spotColor = StudioTheme.AccentPink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "OPTICAL FLOW 60 FPS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = StudioTheme.AccentPink,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Twixtor Speed Ramp: 3.8x Peak",
                    fontSize = 8.sp,
                    color = StudioTheme.TextMuted
                )
            }

            // Aspect Ratio Selector Dropdown Pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Surface(
                    color = StudioTheme.DarkBg.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, StudioTheme.SurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val nextRatio = when (aspectRatio) {
                                    AspectRatioOption.VERTICAL_9_16 -> AspectRatioOption.WIDESCREEN_16_9
                                    AspectRatioOption.WIDESCREEN_16_9 -> AspectRatioOption.SQUARE_1_1
                                    AspectRatioOption.SQUARE_1_1 -> AspectRatioOption.VERTICAL_9_16
                                }
                                viewModel.setAspectRatio(nextRatio)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, tint = StudioTheme.TextSecondary, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(aspectRatio.label.split(" ")[0], fontSize = 8.sp, color = StudioTheme.TextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Floating Transport Playback Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioTheme.DarkBg.copy(alpha = 0.9f))
                    .border(1.dp, StudioTheme.SurfaceBorder, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.stepFrame(-1) }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Step Back", tint = StudioTheme.TextSecondary, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.togglePlay() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(StudioTheme.AccentIndigo)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = { viewModel.stepFrame(1) }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Step Forward", tint = StudioTheme.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ============================================================================
// 7. MULTI-TRACK TIMELINE EDITOR VIEW
// ============================================================================
@Composable
fun TimelineEditorView(viewModel: StudioViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioTheme.DarkBg)
            .padding(horizontal = 8.dp)
    ) {
        // Tool Palette & Timeline Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit Tools Segmented Bar
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TimelineTool.values().forEach { tool ->
                    val isSelected = activeTool == tool
                    Surface(
                        color = if (isSelected) StudioTheme.AccentIndigo else StudioTheme.SurfaceCard,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isSelected) StudioTheme.AccentIndigo else StudioTheme.SurfaceBorder),
                        modifier = Modifier.clickable { viewModel.setActiveTool(tool) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (tool) {
                                    TimelineTool.SELECT -> Icons.Default.NearMe
                                    TimelineTool.RAZOR -> Icons.Default.ContentCut
                                    TimelineTool.TRIM -> Icons.Default.ContentSelect
                                    TimelineTool.KEYFRAME -> Icons.Default.Diamond
                                    TimelineTool.RIPPLE -> Icons.Default.DoubleArrow
                                },
                                contentDescription = tool.name,
                                tint = if (isSelected) Color.White else StudioTheme.TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tool.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else StudioTheme.TextSecondary
                            )
                        }
                    }
                }
            }

            // Quick Actions: Split & Delete
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { viewModel.splitClipAtCurrentPlayhead() },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioTheme.SurfaceCard)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Split Clip", tint = StudioTheme.AccentTeal, modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { viewModel.deleteSelectedClip() },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioTheme.SurfaceCard)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete Clip", tint = StudioTheme.AccentRed, modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { viewModel.addTrack(TrackType.VIDEO) },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioTheme.SurfaceCard)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Track", tint = StudioTheme.AccentIndigo, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Timeline Frame Ruler Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(StudioTheme.SurfaceCard)
                .padding(start = 74.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..5) {
                Text(
                    text = "F:${i * 30}",
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    color = StudioTheme.TextMuted,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tracks Scrollable Timeline Container
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tracks) { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioTheme.SurfaceCard)
                        .border(1.dp, StudioTheme.SurfaceBorder),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track Header Information & Controls
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .background(StudioTheme.SurfaceElevated)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = track.name,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTheme.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = StudioTheme.TextMuted,
                                modifier = Modifier.size(9.dp)
                            )
                            Icon(
                                imageVector = if (track.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = StudioTheme.TextMuted,
                                modifier = Modifier.size(9.dp)
                            )
                            Icon(
                                imageVector = if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = StudioTheme.TextMuted,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }

                    // Clip Segment Blocks Canvas Area
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            track.clips.forEach { clip ->
                                val isSelected = clip.id == selectedClipId
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(0.88f)
                                        .width((clip.durationFrames * 1.4).dp)
                                        .padding(horizontal = 1.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (isSelected) clip.color else clip.color.copy(alpha = 0.65f)
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(5.dp)
                                        )
                                        .clickable { viewModel.selectClip(clip.id) }
                                        .padding(3.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = clip.name,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (clip.keyframesCount > 0) {
                                                Icon(
                                                    Icons.Default.Diamond,
                                                    contentDescription = "Keyframes",
                                                    tint = StudioTheme.AccentAmber,
                                                    modifier = Modifier.size(8.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${clip.speedRampPreset} (${clip.speedScale}x)",
                                            fontSize = 7.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 8. TACTILE VELOCITY CURVE EDITOR VIEW
// ============================================================================
@Composable
fun VelocityCurveCanvasView(viewModel: StudioViewModel) {
    val points by viewModel.velocityPoints.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioTheme.DarkBg)
            .padding(10.dp)
    ) {
        // Curve Editor Header Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BEZIER VELOCITY GRAPH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = StudioTheme.TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Touch-Spline Speed Scaling (0.1x to 5.0x)",
                    fontSize = 9.sp,
                    color = StudioTheme.TextMuted
                )
            }

            // Curve Presets Chips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Flash", "Hero", "Montage", "Reset").forEach { preset ->
                    AssistChip(
                        onClick = { viewModel.applyVelocityPreset(preset) },
                        label = { Text(preset, fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = StudioTheme.SurfaceCard)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Graph Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioTheme.SurfaceCard)
                .border(1.dp, StudioTheme.SurfaceBorder)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Baseline 1.0x speed reference line
                val baseLineY = h - (1.0f / 5.0f * h)
                drawLine(
                    color = StudioTheme.AccentIndigo.copy(alpha = 0.4f),
                    start = Offset(0f, baseLineY),
                    end = Offset(w, baseLineY),
                    strokeWidth = 2f
                )

                // Cubic Bezier Path Rendering
                val path = Path()
                points.forEachIndexed { idx, pt ->
                    val px = pt.x * w
                    val py = h - (pt.y / 5.0f * h)
                    if (idx == 0) {
                        path.moveTo(px, py)
                    } else {
                        val prevPt = points[idx - 1]
                        val prevPx = prevPt.x * w
                        val prevPy = h - (prevPt.y / 5.0f * h)
                        val controlX1 = prevPx + (px - prevPx) / 2
                        val controlX2 = prevPx + (px - prevPx) / 2
                        path.cubicTo(controlX1, prevPy, controlX2, py, px, py)
                    }
                }

                drawPath(
                    path = path,
                    brush = StudioTheme.StudioGradient,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )

                // Render Tangent Node Control Handles
                points.forEach { pt ->
                    val px = pt.x * w
                    val py = h - (pt.y / 5.0f * h)
                    drawCircle(color = Color.White, radius = 9f, center = Offset(px, py))
                    drawCircle(color = StudioTheme.AccentIndigo, radius = 5f, center = Offset(px, py))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Speed Stats Summary Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioTheme.SurfaceElevated)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("PEAK SPEED", fontSize = 8.sp, color = StudioTheme.TextMuted)
                Text("4.8x Multiplier", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTheme.AccentPink)
            }
            Column {
                Text("MIN SLOW-MO", fontSize = 8.sp, color = StudioTheme.TextMuted)
                Text("0.2x Optical Flow", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTheme.AccentTeal)
            }
            Column {
                Text("INTERPOLATION", fontSize = 8.sp, color = StudioTheme.TextMuted)
                Text("Twixtor Vector", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTheme.AccentPurple)
            }
        }
    }
}

// ============================================================================
// 9. NODE COLOR & SHADER COMPOSITOR VIEW
// ============================================================================
@Composable
fun NodeCompositorCanvasView(viewModel: StudioViewModel) {
    val nodes by viewModel.nodes.collectAsState()
    val selectedNodeId by viewModel.selectedNodeId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioTheme.DarkBg)
            .padding(10.dp)
    ) {
        // Compositor Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NODE COLOR & SHADER COMPOSITOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = StudioTheme.TextPrimary
                )
                Text(
                    text = "Non-Destructive DaVinci-Style Node Graph",
                    fontSize = 9.sp,
                    color = StudioTheme.TextMuted
                )
            }

            Button(
                onClick = { /* Add Shader Node */ },
                colors = ButtonDefaults.buttonColors(containerColor = StudioTheme.SurfaceElevated),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Node", fontSize = 9.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Node Visual Graph Workspace Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioTheme.SurfaceCard)
                .border(1.dp, StudioTheme.SurfaceBorder)
        ) {
            // Render Bezier Wire Cables Between Nodes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                for (i in 0 until nodes.size - 1) {
                    val p1 = nodes[i].position + Offset(100f, 35f)
                    val p2 = nodes[i + 1].position + Offset(0f, 35f)
                    path.reset()
                    path.moveTo(p1.x, p1.y)
                    path.cubicTo(p1.x + 50f, p1.y, p2.x - 50f, p2.y, p2.x, p2.y)
                    drawPath(
                        path = path,
                        color = StudioTheme.AccentIndigo.copy(alpha = 0.8f),
                        style = Stroke(width = 3.5f)
                    )
                }
            }

            // Draggable Node Items Rendering
            nodes.forEach { node ->
                val isSelected = node.id == selectedNodeId
                Box(
                    modifier = Modifier
                        .offset(x = node.position.x.dp, y = node.position.y.dp)
                        .width(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioTheme.SurfaceElevated)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else node.color.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.selectNode(node.id) }
                        .pointerInput(node.id) {
                            detectTransformGestures { _, pan, _, _ ->
                                viewModel.updateNodePosition(node.id, node.position + pan)
                            }
                        }
                        .padding(6.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(node.color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = node.title,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioTheme.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(node.category, fontSize = 7.sp, color = StudioTheme.TextMuted)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Selected Node Parameters Drawer
        val activeNode = nodes.find { it.id == selectedNodeId } ?: nodes.first()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioTheme.SurfaceElevated)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NODE INSPECTOR: ${activeNode.title.uppercase()}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeNode.color
                )
                Text(activeNode.category, fontSize = 8.sp, color = StudioTheme.TextMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                activeNode.parameters.forEach { (key, value) ->
                    Column {
                        Text(key, fontSize = 7.sp, color = StudioTheme.TextMuted)
                        Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StudioTheme.TextPrimary)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 10. AI COPILOT ASSISTANT VIEW
// ============================================================================
@Composable
fun AICopilotView(viewModel: StudioViewModel) {
    val messages by viewModel.aiMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioTheme.DarkBg)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = StudioTheme.AccentPurple, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("MOTION AI COPILOT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = StudioTheme.TextPrimary)
            }
            Text("Automated Edit Assistant", fontSize = 8.sp, color = StudioTheme.TextMuted)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Messages History Box
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(messages) { msg ->
                val isUser = msg.sender == "You"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isUser) StudioTheme.AccentIndigo else StudioTheme.SurfaceCard)
                            .border(1.dp, StudioTheme.SurfaceBorder, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(msg.text, fontSize = 10.sp, color = Color.White)
                            if (msg.isActionable) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { viewModel.applyVelocityPreset("Flash") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioTheme.AccentPurple),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(msg.actionLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Prompt Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask AI to sync beats, adjust velocity, add glows...", fontSize = 9.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioTheme.AccentIndigo,
                    unfocusedBorderColor = StudioTheme.SurfaceBorder,
                    focusedContainerColor = StudioTheme.SurfaceCard,
                    unfocusedContainerColor = StudioTheme.SurfaceCard
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendAIPrompt(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioTheme.AccentIndigo)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send Prompt", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ============================================================================
// 11. PROJECT MEDIA VAULT VIEW
// ============================================================================
@Composable
fun AssetVaultBrowser(viewModel: StudioViewModel) {
    val assets by viewModel.importedFiles.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioTheme.DarkBg)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PROJECT MEDIA VAULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = StudioTheme.TextPrimary
                )
                Text(
                    text = "Video Clips, Audio Tracks, 3D LUTs & Shader FX",
                    fontSize = 9.sp,
                    color = StudioTheme.TextMuted
                )
            }

            Button(
                onClick = { /* Pick file from system storage */ },
                colors = ButtonDefaults.buttonColors(containerColor = StudioTheme.AccentIndigo),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import File", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grid View of Project Assets
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(assets) { asset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioTheme.SurfaceCard)
                        .border(1.dp, StudioTheme.SurfaceBorder)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(asset.colorAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (asset.type) {
                                MediaType.VIDEO -> Icons.Default.Movie
                                MediaType.AUDIO -> Icons.Default.Audiotrack
                                MediaType.IMAGE -> Icons.Default.Image
                                MediaType.LUT -> Icons.Default.Palette
                                MediaType.PRESET -> Icons.Default.Extension
                                MediaType.SHADER -> Icons.Default.Code
                            },
                            contentDescription = null,
                            tint = asset.colorAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Text(
                            text = asset.title,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTheme.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(asset.durationText, fontSize = 7.sp, color = StudioTheme.TextMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(asset.resolution, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = asset.colorAccent)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 12. EXPORT RENDER DIALOG MODAL
// ============================================================================
@Composable
fun ExportModalDialog(viewModel: StudioViewModel) {
    val config by viewModel.exportConfig.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.setExportModalOpen(false) },
        containerColor = StudioTheme.SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = StudioTheme.AccentIndigo)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export & Render Master", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StudioTheme.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("RESOLUTION & FRAME RATE", fontSize = 8.sp, color = StudioTheme.TextMuted)
                    Text("${config.resolution} @ ${config.fps} FPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTheme.TextPrimary)
                }
                Column {
                    Text("CODEC & CONTAINER", fontSize = 8.sp, color = StudioTheme.TextMuted)
                    Text("${config.format} (${config.bitrateMbps} Mbps)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTheme.AccentTeal)
                }
                Column {
                    Text("FRAME INTERPOLATION", fontSize = 8.sp, color = StudioTheme.TextMuted)
                    Text("Twixtor Optical Flow Vector Motion Blur", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTheme.AccentPink)
                }
                Surface(
                    color = StudioTheme.SurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Estimated File Size:", fontSize = 9.sp, color = StudioTheme.TextMuted)
                        Text("185.4 MB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StudioTheme.AccentAmber)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.setExportModalOpen(false) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioTheme.AccentIndigo)
            ) {
                Text("Start Render", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.setExportModalOpen(false) }) {
                Text("Cancel", fontSize = 10.sp, color = StudioTheme.TextMuted)
            }
        }
    )
}

// ============================================================================
// 13. BOTTOM NAVIGATION TAB BAR
// ============================================================================
@Composable
fun NavigationTabBar(
    activeTab: StudioTab,
    onTabSelected: (StudioTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(StudioTheme.SurfaceCard)
            .border(1.dp, StudioTheme.SurfaceBorder),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioTab.values().forEach { tab ->
            val isSelected = activeTab == tab
            val tint = if (isSelected) StudioTheme.AccentIndigo else StudioTheme.TextMuted

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = when (tab) {
                        StudioTab.TIMELINE -> Icons.Default.ViewTimeline
                        StudioTab.VELOCITY -> Icons.Default.Speed
                        StudioTab.NODES -> Icons.Default.AccountTree
                        StudioTab.AI_ASSIST -> Icons.Default.AutoAwesome
                        StudioTab.VAULT -> Icons.Default.FolderOpen
                    },
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    fontSize = 8.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = tint
                )
            }
        }
    }
}

// ============================================================================
// 14. ROOT APP COMPOSABLE
// ============================================================================
@Composable
fun MotionStudioApp(viewModel: StudioViewModel = remember { StudioViewModel() }) {
    val activeTab by viewModel.activeTab.collectAsState()
    val isExportModalOpen by viewModel.isExportModalOpen.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioTheme.DarkBg)
    ) {
        // 1. Top Header Bar
        TopAppBarHeader(viewModel)

        // 2. Video Preview Canvas Viewport
        VideoPreviewPlayer(viewModel)

        // 3. Dynamic Interactive Studio Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeTab) {
                StudioTab.TIMELINE -> TimelineEditorView(viewModel)
                StudioTab.VELOCITY -> VelocityCurveCanvasView(viewModel)
                StudioTab.NODES -> NodeCompositorCanvasView(viewModel)
                StudioTab.AI_ASSIST -> AICopilotView(viewModel)
                StudioTab.VAULT -> AssetVaultBrowser(viewModel)
            }
        }

        // 4. Studio Tab Bar Navigation
        NavigationTabBar(
            activeTab = activeTab,
            onTabSelected = { viewModel.setActiveTab(it) }
        )

        // 5. Render Modal Dialog
        if (isExportModalOpen) {
            ExportModalDialog(viewModel)
        }
    }
}

@file:Suppress("FunctionName", "unused")

package com.motionstudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Effects
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Export
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * MOTION STUDIO — Ui.kt
 * ---------------------
 * Single Compose UI shell for the complete Motion Studio application.
 *
 * IMPORTANT:
 * 1. Put the supplied logo at:
 *      app/src/main/res/drawable/ic_motion_studio_logo.png
 * 2. This file intentionally does not create fake media, render jobs,
 *    effects, plugins, AI models, or export results. The UI exposes callbacks
 *    so the real Part 1–6 engines can be connected by the application layer.
 * 3. All visible Motion Studio branding uses the same supplied logo resource.
 *
 * Suggested application entry:
 *      MotionStudioApp()
 *
 * Required dependencies are standard AndroidX Compose / Material3 plus the
 * Activity Result Compose artifact for the import/export document pickers.
 */

private val MotionStudioDark = darkColorScheme(
    primary = Color(0xFFB79CFF),
    onPrimary = Color(0xFF170D2E),
    secondary = Color(0xFF9E8AD7),
    background = Color(0xFF070617),
    surface = Color(0xFF0D0C1F),
    surfaceVariant = Color(0xFF17142B),
    onBackground = Color(0xFFF3F0FF),
    onSurface = Color(0xFFF3F0FF),
    onSurfaceVariant = Color(0xFFB8B3CB),
    outline = Color(0xFF3B3650)
)

enum class MotionStudioScreen {
    HOME, EDITOR, EFFECTS, TRANSITIONS, NODES, AUDIO, COLOR, TRACKING,
    ASSETS, PLUGINS, EXPORT, QUEUE, PERFORMANCE, TUTORIALS, SETTINGS
}

data class UiProject(
    val id: String,
    val name: String,
    val resolution: String = "1920 × 1080",
    val fps: String = "30 fps",
    val duration: String = "00:00:10",
    val modified: String = ""
)

data class UiLayer(
    val id: String,
    val name: String,
    val kind: String,
    val startUs: Long = 0L,
    val durationUs: Long = 1_000_000L,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val hasKeyframes: Boolean = false,
    val effectCount: Int = 0
)

data class UiAsset(
    val id: String,
    val name: String,
    val category: String,
    val sizeText: String = "",
    val compatible: Boolean = true,
    val installed: Boolean = true
)

data class UiEffect(
    val id: String,
    val name: String,
    val category: String,
    val installed: Boolean = true,
    val compatible: Boolean = true
)

data class UiExportProfile(
    val id: String,
    val name: String,
    val resolution: String,
    val fps: String,
    val codec: String,
    val container: String,
    val quality: String
)

data class MotionStudioUiState(
    val project: UiProject? = null,
    val projects: List<UiProject> = emptyList(),
    val layers: List<UiLayer> = emptyList(),
    val assets: List<UiAsset> = emptyList(),
    val effects: List<UiEffect> = emptyList(),
    val exportProfiles: List<UiExportProfile> = emptyList(),
    val currentTimeUs: Long = 0L,
    val compositionDurationUs: Long = 10_000_000L,
    val selectedLayerId: String? = null,
    val isPlaying: Boolean = false,
    val autosaveEnabled: Boolean = true,
    val cacheSizeText: String = "—",
    val storageFreeText: String = "—",
    val renderProgress: Float? = null,
    val diagnostics: List<String> = emptyList()
)

data class MotionStudioUiCallbacks(
    val onScreen: (MotionStudioScreen) -> Unit = {},
    val onCreateProject: () -> Unit = {},
    val onOpenProject: () -> Unit = {},
    val onSaveProject: () -> Unit = {},
    val onImportUris: (List<Uri>) -> Unit = {},
    val onDeleteProject: (String) -> Unit = {},
    val onSelectLayer: (String?) -> Unit = {},
    val onToggleLayerVisibility: (String) -> Unit = {},
    val onToggleLayerLock: (String) -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onSplit: () -> Unit = {},
    val onDeleteSelection: () -> Unit = {},
    val onAddLayer: (String) -> Unit = {},
    val onAddEffect: (String) -> Unit = {},
    val onApplyEffect: (String, String) -> Unit = { _, _ -> },
    val onAddTransition: (String) -> Unit = {},
    val onAddNode: (String) -> Unit = {},
    val onExport: (UiExportProfile, Uri?) -> Unit = { _, _ -> },
    val onCancelExport: () -> Unit = {},
    val onClearCache: () -> Unit = {},
    val onRelinkMedia: () -> Unit = {},
    val onRunDiagnostics: () -> Unit = {},
    val onImportAsset: (List<Uri>) -> Unit = {},
    val onOpenSettings: () -> Unit = {}
)

@Composable
fun MotionStudioApp(
    state: MotionStudioUiState = MotionStudioUiState(),
    callbacks: MotionStudioUiCallbacks = MotionStudioUiCallbacks()
) {
    MaterialTheme(colorScheme = MotionStudioDark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MotionStudioShell(state, callbacks)
        }
    }
}

@Composable
private fun MotionStudioShell(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks
) {
    var screen by remember { mutableStateOf(MotionStudioScreen.HOME) }
    var drawerOpen by remember { mutableStateOf(false) }

    fun navigate(target: MotionStudioScreen) {
        screen = target
        drawerOpen = false
        callbacks.onScreen(target)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                MotionStudioTopBar(
                    screen = screen,
                    projectName = state.project?.name,
                    onMenu = { drawerOpen = true },
                    onBack = if (screen == MotionStudioScreen.HOME) null else ({ navigate(MotionStudioScreen.HOME) }),
                    onSave = callbacks.onSaveProject,
                    onExport = { navigate(MotionStudioScreen.EXPORT) }
                )
            },
            bottomBar = {
                if (screen == MotionStudioScreen.EDITOR) {
                    EditorTransportBar(
                        state = state,
                        callbacks = callbacks
                    )
                } else {
                    MotionStudioBottomNav(screen, ::navigate)
                }
            },
            contentWindowInsets = WindowInsets.navigationBars
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (screen) {
                    MotionStudioScreen.HOME -> HomeScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.EDITOR -> EditorScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.EFFECTS -> EffectsScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.TRANSITIONS -> TransitionsScreen(callbacks, ::navigate)
                    MotionStudioScreen.NODES -> NodesScreen(callbacks, ::navigate)
                    MotionStudioScreen.AUDIO -> AudioScreen(callbacks, ::navigate)
                    MotionStudioScreen.COLOR -> ColorScreen(callbacks, ::navigate)
                    MotionStudioScreen.TRACKING -> TrackingScreen(callbacks, ::navigate)
                    MotionStudioScreen.ASSETS -> AssetsScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.PLUGINS -> PluginsScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.EXPORT -> ExportScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.QUEUE -> QueueScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.PERFORMANCE -> PerformanceScreen(state, callbacks, ::navigate)
                    MotionStudioScreen.TUTORIALS -> TutorialsScreen(callbacks, ::navigate)
                    MotionStudioScreen.SETTINGS -> SettingsScreen(state, callbacks, ::navigate)
                }
            }
        }

        if (drawerOpen) {
            MotionStudioDrawer(
                selected = screen,
                onDismiss = { drawerOpen = false },
                onNavigate = ::navigate
            )
        }
    }
}

@Composable
private fun MotionStudioTopBar(
    screen: MotionStudioScreen,
    projectName: String?,
    onMenu: () -> Unit,
    onBack: (() -> Unit)?,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MotionStudioLogo(34.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (screen == MotionStudioScreen.HOME) "Motion Studio"
                        else screenTitle(screen),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (projectName != null && screen != MotionStudioScreen.HOME) {
                        Text(
                            projectName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { if (onBack != null) onBack() else onMenu() }) {
                Icon(
                    if (onBack != null) Icons.Filled.ArrowBack else Icons.Filled.Menu,
                    contentDescription = if (onBack != null) "Back" else "Menu"
                )
            }
        },
        actions = {
            if (screen == MotionStudioScreen.EDITOR) {
                IconButton(onClick = onSave) {
                    Icon(Icons.Outlined.Save, "Save")
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Filled.Export, "Export")
                }
            }
        }
    )
}

@Composable
private fun MotionStudioLogo(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(com.motionstudio.R.drawable.ic_motion_studio_logo),
        contentDescription = "Motion Studio",
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.19f)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun MotionStudioBottomNav(
    screen: MotionStudioScreen,
    navigate: (MotionStudioScreen) -> Unit
) {
    val items = listOf(
        MotionStudioScreen.HOME to (Icons.Filled.Home to "Home"),
        MotionStudioScreen.EDITOR to (Icons.Filled.Movie to "Editor"),
        MotionStudioScreen.EFFECTS to (Icons.Filled.Effects to "Effects"),
        MotionStudioScreen.ASSETS to (Icons.Filled.Folder to "Assets"),
        MotionStudioScreen.SETTINGS to (Icons.Filled.Settings to "Settings")
    )
    Surface(tonalElevation = 5.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (target, iconAndName) ->
                val selected = screen == target
                NavigationItem(
                    icon = iconAndName.first,
                    label = iconAndName.second,
                    selected = selected,
                    onClick = { navigate(target) }
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MotionStudioDrawer(
    selected: MotionStudioScreen,
    onDismiss: () -> Unit,
    onNavigate: (MotionStudioScreen) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 330.dp)
                .clickable(enabled = false) {},
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MotionStudioLogo(52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Motion Studio", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Professional mobile editor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(22.dp))
                DrawerSection("WORKSPACE")
                DrawerItem(Icons.Filled.Home, "Home", selected == MotionStudioScreen.HOME) { onNavigate(MotionStudioScreen.HOME) }
                DrawerItem(Icons.Filled.Movie, "Editor", selected == MotionStudioScreen.EDITOR) { onNavigate(MotionStudioScreen.EDITOR) }
                DrawerItem(Icons.Filled.Effects, "Effects", selected == MotionStudioScreen.EFFECTS) { onNavigate(MotionStudioScreen.EFFECTS) }
                DrawerItem(Icons.Filled.ContentCut, "Transitions", selected == MotionStudioScreen.TRANSITIONS) { onNavigate(MotionStudioScreen.TRANSITIONS) }
                DrawerItem(Icons.Outlined.Timeline, "Graph / Keyframes", selected == MotionStudioScreen.NODES) { onNavigate(MotionStudioScreen.NODES) }
                DrawerItem(Icons.Filled.Layers, "Nodes / Compositor", selected == MotionStudioScreen.NODES) { onNavigate(MotionStudioScreen.NODES) }
                DrawerItem(Icons.Filled.Equalizer, "Audio", selected == MotionStudioScreen.AUDIO) { onNavigate(MotionStudioScreen.AUDIO) }
                DrawerItem(Icons.Filled.ColorLens, "Color", selected == MotionStudioScreen.COLOR) { onNavigate(MotionStudioScreen.COLOR) }
                DrawerItem(Icons.Filled.Tune, "Tracking / AI", selected == MotionStudioScreen.TRACKING) { onNavigate(MotionStudioScreen.TRACKING) }

                Spacer(Modifier.height(14.dp))
                DrawerSection("ASSETS")
                DrawerItem(Icons.Filled.Folder, "Asset Library", selected == MotionStudioScreen.ASSETS) { onNavigate(MotionStudioScreen.ASSETS) }
                DrawerItem(Icons.Outlined.Extension, "AE Plugins", selected == MotionStudioScreen.PLUGINS) { onNavigate(MotionStudioScreen.PLUGINS) }

                Spacer(Modifier.height(14.dp))
                DrawerSection("OUTPUT")
                DrawerItem(Icons.Filled.Export, "Export", selected == MotionStudioScreen.EXPORT) { onNavigate(MotionStudioScreen.EXPORT) }
                DrawerItem(Icons.Filled.Dashboard, "Render Queue", selected == MotionStudioScreen.QUEUE) { onNavigate(MotionStudioScreen.QUEUE) }
                DrawerItem(Icons.Outlined.Memory, "Performance", selected == MotionStudioScreen.PERFORMANCE) { onNavigate(MotionStudioScreen.PERFORMANCE) }

                Spacer(Modifier.height(14.dp))
                DrawerSection("SYSTEM")
                DrawerItem(Icons.Filled.HelpOutline, "Tutorials", selected == MotionStudioScreen.TUTORIALS) { onNavigate(MotionStudioScreen.TUTORIALS) }
                DrawerItem(Icons.Filled.Settings, "Settings", selected == MotionStudioScreen.SETTINGS) { onNavigate(MotionStudioScreen.SETTINGS) }
            }
        }
    }
}

@Composable
private fun DrawerSection(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, text, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun HomeScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) callbacks.onImportUris(uris) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MotionStudioLogo(68.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Motion Studio", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Professional editing, compositing and finishing.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeAction(Modifier.weight(1f), Icons.Filled.Add, "New Project") { callbacks.onCreateProject() }
                HomeAction(Modifier.weight(1f), Icons.Outlined.FolderOpen, "Open Project") { callbacks.onOpenProject() }
                HomeAction(Modifier.weight(1f), Icons.Filled.Folder, "Import Media") {
                    importLauncher.launch(arrayOf("*/*"))
                }
            }
        }

        item { SectionHeader("Current project") }
        item {
            if (state.project == null) {
                EmptyCard(
                    title = "No project open",
                    message = "Create or open a project to enter the full editor."
                )
            } else {
                ProjectCard(state.project, onOpen = { navigate(MotionStudioScreen.EDITOR) })
            }
        }

        item { SectionHeader("Workspace") }
        item {
            WorkspaceGrid(navigate)
        }

        item { SectionHeader("Recent projects") }
        if (state.projects.isEmpty()) {
            item {
                EmptyCard(
                    title = "No recent projects",
                    message = "Projects supplied by the application layer will appear here."
                )
            }
        } else {
            items(state.projects, key = { it.id }) { project ->
                ProjectRow(project) { navigate(MotionStudioScreen.EDITOR) }
            }
        }
    }
}

@Composable
private fun HomeAction(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(92.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, label, modifier = Modifier.size(27.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ProjectCard(project: UiProject, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(project.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text("${project.resolution}  •  ${project.fps}  •  ${project.duration}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (project.modified.isNotBlank()) Text("Modified ${project.modified}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpen) { Text("Open Editor") }
        }
    }
}

@Composable
private fun ProjectRow(project: UiProject, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MotionStudioLogo(42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.SemiBold)
                Text("${project.resolution}  •  ${project.fps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ArrowBack, "Open", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WorkspaceGrid(navigate: (MotionStudioScreen) -> Unit) {
    val items = listOf(
        Triple("Effects", Icons.Filled.Effects, MotionStudioScreen.EFFECTS),
        Triple("Transitions", Icons.Filled.ContentCut, MotionStudioScreen.TRANSITIONS),
        Triple("Nodes", Icons.Filled.Layers, MotionStudioScreen.NODES),
        Triple("Audio", Icons.Filled.MusicNote, MotionStudioScreen.AUDIO),
        Triple("Color", Icons.Filled.ColorLens, MotionStudioScreen.COLOR),
        Triple("Tracking", Icons.Filled.Tune, MotionStudioScreen.TRACKING),
        Triple("Assets", Icons.Filled.Folder, MotionStudioScreen.ASSETS),
        Triple("Plugins", Icons.Outlined.Extension, MotionStudioScreen.PLUGINS)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Surface(
                        Modifier
                            .weight(1f)
                            .height(74.dp)
                            .clickable { navigate(item.third) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.second, item.first, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(item.first, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditorScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    var inspectorTab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        EditorToolbar(callbacks, navigate)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            EditorPreview(state)
        }

        TabRow(selectedTabIndex = inspectorTab) {
            listOf("Transform", "Effects", "Masks", "Text").forEachIndexed { index, label ->
                Tab(
                    selected = inspectorTab == index,
                    onClick = { inspectorTab = index },
                    text = { Text(label) }
                )
            }
        }

        when (inspectorTab) {
            0 -> TransformInspector(state, callbacks)
            1 -> EffectInspector(state, callbacks, navigate)
            2 -> MaskInspector()
            3 -> TextInspector()
        }

        TimelinePanel(state, callbacks)
    }
}

@Composable
private fun EditorToolbar(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactTool(Icons.Filled.Undo, "Undo", callbacks.onUndo)
        CompactTool(Icons.Filled.Redo, "Redo", callbacks.onRedo)
        Divider(Modifier.width(1.dp).height(28.dp))
        CompactTool(Icons.Filled.ContentCut, "Split", callbacks.onSplit)
        CompactTool(Icons.Filled.Delete, "Delete", callbacks.onDeleteSelection)
        CompactTool(Icons.Filled.Layers, "Layers") { }
        CompactTool(Icons.Outlined.Timeline, "Graph") { navigate(MotionStudioScreen.NODES) }
        CompactTool(Icons.Filled.Effects, "Effects") { navigate(MotionStudioScreen.EFFECTS) }
        CompactTool(Icons.Filled.ColorLens, "Color") { navigate(MotionStudioScreen.COLOR) }
        CompactTool(Icons.Filled.Tune, "Track") { navigate(MotionStudioScreen.TRACKING) }
    }
}

@Composable
private fun CompactTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Icon(icon, label, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EditorPreview(state: MotionStudioUiState) {
    Surface(
        Modifier.fillMaxSize(),
        color = Color(0xFF03030A),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.project == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MotionStudioLogo(72.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("No composition loaded", fontWeight = FontWeight.SemiBold)
                }
            } else {
                // The actual Part 2 compositor/renderer supplies the frame here.
                // This UI intentionally does not fabricate a preview frame.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Movie, "Preview", modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Renderer output", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${state.project.resolution}  •  ${state.project.fps}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformInspector(state: MotionStudioUiState, callbacks: MotionStudioUiCallbacks) {
    val layer = state.layers.firstOrNull { it.id == state.selectedLayerId }
    if (layer == null) {
        EmptyInspector("Select a layer to edit transform properties.")
        return
    }
    var opacity by remember(layer.id) { mutableFloatStateOf(layer.opacity) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(154.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueBox("Position", "Engine")
            ValueBox("Scale", "Engine")
            ValueBox("Rotation", "Engine")
            ValueBox("Anchor", "Engine")
        }
        Spacer(Modifier.height(8.dp))
        Text("Opacity  ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            valueRange = 0f..1f
        )
    }
}

@Composable
private fun ValueBox(title: String, value: String) {
    Surface(
        Modifier.weight(1f),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(9.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EffectInspector(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    val layer = state.layers.firstOrNull { it.id == state.selectedLayerId }
    Column(Modifier.height(154.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (layer == null) {
            EmptyInspector("Select a layer to inspect effects.")
        } else {
            Text("${layer.effectCount} effect(s) on ${layer.name}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { navigate(MotionStudioScreen.EFFECTS) }) { Text("Add Effect") }
                OutlinedButton(onClick = { navigate(MotionStudioScreen.TRANSITIONS) }) { Text("Transitions") }
            }
        }
    }
}

@Composable
private fun MaskInspector() {
    Column(Modifier.height(154.dp).padding(12.dp)) {
        Text("Masks", fontWeight = FontWeight.SemiBold)
        Text("Add, subtract, intersect, feather and animate masks through the Part 1/3 mask systems.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Add") })
            AssistChip(onClick = {}, label = { Text("Subtract") })
            AssistChip(onClick = {}, label = { Text("Intersect") })
        }
    }
}

@Composable
private fun TextInspector() {
    var text by remember { mutableStateOf("") }
    Column(Modifier.height(154.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Text layer", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Content") }
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueBox("Font", "Engine")
            ValueBox("Size", "Engine")
            ValueBox("Tracking", "Engine")
        }
    }
}

@Composable
private fun TimelinePanel(state: MotionStudioUiState, callbacks: MotionStudioUiCallbacks) {
    val max = state.compositionDurationUs.coerceAtLeast(1L)
    val position = (state.currentTimeUs.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        TimelineHeader(state)
        TimelineRuler(position, callbacks)
        if (state.layers.isEmpty()) {
            EmptyCard("No layers", "Layers supplied by the editor engine will appear here.")
        } else {
            LazyColumn(
                Modifier.height(190.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                items(state.layers, key = { it.id }) { layer ->
                    TimelineLayerRow(layer, layer.id == state.selectedLayerId, callbacks)
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(state: MotionStudioUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatTime(state.currentTimeUs), fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text("${state.layers.size} layers", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimelineRuler(position: Float, callbacks: MotionStudioUiCallbacks) {
    var local by remember(position) { mutableFloatStateOf(position) }
    Column(Modifier.padding(horizontal = 10.dp)) {
        Slider(
            value = local,
            onValueChange = {
                local = it
                callbacks.onSeek(it.toLong())
            }
        )
    }
}

@Composable
private fun TimelineLayerRow(
    layer: UiLayer,
    selected: Boolean,
    callbacks: MotionStudioUiCallbacks
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { callbacks.onSelectLayer(layer.id) },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.DragHandle, "Reorder", modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Icon(layerIcon(layer.kind), layer.kind, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(layer.name, Modifier.width(125.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(
                Modifier
                    .weight(1f)
                    .height(27.dp),
                shape = RoundedCornerShape(5.dp),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (layer.hasKeyframes) {
                        Text("◆", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        if (layer.effectCount > 0) "${layer.effectCount} fx" else layer.kind,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(onClick = { callbacks.onToggleLayerVisibility(layer.id) }) {
                Icon(
                    if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    "Visibility"
                )
            }
            IconButton(onClick = { callbacks.onToggleLayerLock(layer.id) }) {
                Icon(if (layer.locked) Icons.Filled.Lock else Icons.Filled.Lock, "Lock")
            }
        }
    }
}

@Composable
private fun EditorTransportBar(state: MotionStudioUiState, callbacks: MotionStudioUiCallbacks) {
    Surface(tonalElevation = 7.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { callbacks.onSeek(0L) }) {
                Icon(Icons.Filled.Refresh, "Start")
            }
            IconButton(onClick = callbacks.onPlayPause) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play")
            }
            Text(formatTime(state.currentTimeUs), modifier = Modifier.padding(horizontal = 12.dp))
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(state.compositionDurationUs), modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun EffectsScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All", "Blur", "Color", "Stylize", "Distort", "Keying", "Motion", "Plugin")
    val filtered = state.effects.filter {
        (category == "All" || it.category.equals(category, true)) &&
            (query.isBlank() || it.name.contains(query, true))
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("Search effects") },
            leadingIcon = { Icon(Icons.Filled.Search, "Search") }
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { c ->
                AssistChip(onClick = { category = c }, label = { Text(c) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyCard("No effects available", "The real EffectLibrary/registry can populate this list.")
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filtered, key = { it.id }) { effect ->
                    EffectRow(effect) {
                        callbacks.onAddEffect(effect.id)
                        navigate(MotionStudioScreen.EDITOR)
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectRow(effect: UiEffect, onAdd: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Effects, "Effect")
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(effect.name, fontWeight = FontWeight.SemiBold)
                Text("${effect.category}${if (!effect.compatible) " • Compatibility required" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd, enabled = effect.compatible) { Text("Add") }
        }
    }
}

@Composable
private fun TransitionsScreen(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    val transitions = listOf(
        "Cross Dissolve", "Dip to Color", "Wipe", "Slide", "Zoom",
        "Directional Blur", "Push", "Morph", "Custom"
    )
    Column(Modifier.fillMaxSize()) {
        SectionHeader("Transitions")
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(transitions) { name ->
                Surface(
                    Modifier.fillMaxWidth().clickable {
                        callbacks.onAddTransition(name)
                        navigate(MotionStudioScreen.EDITOR)
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCut, name)
                        Spacer(Modifier.width(12.dp))
                        Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Icon(Icons.Filled.Add, "Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun NodesScreen(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    var zoom by remember { mutableFloatStateOf(0.75f) }
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF05050D))
                .pointerInput(Unit) {
                    detectDragGestures { _, _ -> }
                }
        ) {
            val spacing = (50f * zoom).coerceAtLeast(24f)
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0xFF171626), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height))
                x += spacing
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0xFF171626), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y))
                y += spacing
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Button(onClick = { callbacks.onAddNode("Input") }) { Text("Input") }
                OutlinedButton(onClick = { callbacks.onAddNode("Effect") }) { Text("Effect") }
                OutlinedButton(onClick = { callbacks.onAddNode("Composite") }) { Text("Composite") }
                OutlinedButton(onClick = { callbacks.onAddNode("Output") }) { Text("Output") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { zoom = (zoom + 0.1f).coerceAtMost(2f) }) { Text("+") }
                OutlinedButton(onClick = { zoom = (zoom - 0.1f).coerceAtLeast(0.25f) }) { Text("−") }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Node compositor", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Connect the real Part 1/3 graph and Part 2 RenderGraph here. The canvas does not invent graph nodes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { navigate(MotionStudioScreen.EDITOR) }) { Text("Back to Editor") }
                        TextButton(onClick = { navigate(MotionStudioScreen.EFFECTS) }) { Text("Effect Library") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioScreen(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader("Audio workspace")
        FeatureCard(Icons.Filled.MusicNote, "Mixing", "Gain, pan, mute and solo are provided by Part 4 AudioEngine.")
        FeatureCard(Icons.Filled.Equalizer, "Waveform & analysis", "Waveform, RMS/peak and deterministic beat/onset analysis.")
        FeatureCard(Icons.Filled.Speed, "Time remapping", "Speed and time-remap controls connect to Part 4.")
        FeatureCard(Icons.Filled.Tune, "Audio cache", "Keep decoded audio on the managed cache boundary.")
        Button(onClick = { navigate(MotionStudioScreen.EDITOR) }) { Text("Return to Editor") }
    }
}

@Composable
private fun ColorScreen(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        SectionHeader("Color")
        Text("Primary correction", fontWeight = FontWeight.SemiBold)
        SliderRow("Exposure", exposure, -2f..2f) { exposure = it }
        SliderRow("Contrast", contrast, -1f..1f) { contrast = it }
        SliderRow("Saturation", saturation, 0f..2f) { saturation = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureCardSmall(Icons.Filled.WbSunny, "Curves")
            FeatureCardSmall(Icons.Filled.ColorLens, "LUT")
            FeatureCardSmall(Icons.Filled.Dashboard, "Scopes")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { navigate(MotionStudioScreen.EDITOR) }) { Text("Return to Editor") }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Text("$title  ${"%.2f".format(value)}", style = MaterialTheme.typography.labelMedium)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun TrackingScreen(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        SectionHeader("Tracking & AI")
        FeatureCard(Icons.Filled.Tune, "Object tracking", "Point, planar and tracker-engine adapters from Part 4.")
        FeatureCard(Icons.Filled.Refresh, "Stabilization", "Optical-flow/reference stabilization pipeline.")
        FeatureCard(Icons.Filled.Visibility, "Background removal", "Uses the supplied inference provider/model boundary; no model is fabricated here.")
        FeatureCard(Icons.Filled.CameraAlt, "Face tools", "Face landmark adapter boundary from Part 4.")
        FeatureCard(Icons.Filled.Speed, "Frame interpolation", "Frame blending/interpolation controls.")
        Button(onClick = { navigate(MotionStudioScreen.EDITOR) }) { Text("Return to Editor") }
    }
}

@Composable
private fun AssetsScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) callbacks.onImportAsset(uris) }

    var query by remember { mutableStateOf("") }
    val assets = state.assets.filter { query.isBlank() || it.name.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search assets") },
                leadingIcon = { Icon(Icons.Filled.Search, "Search") }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, "Import asset")
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AssistChip(onClick = {}, label = { Text("All") })
            AssistChip(onClick = {}, label = { Text("Effects") })
            AssistChip(onClick = {}, label = { Text("Presets") })
            AssistChip(onClick = { navigate(MotionStudioScreen.PLUGINS) }, label = { Text("Plugins") })
        }
        if (assets.isEmpty()) {
            EmptyCard("No assets", "The Part 5 AssetRegistry/EffectLibrary can populate this library.")
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(assets, key = { it.id }) { asset ->
                    AssetRow(asset)
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: UiAsset) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(assetIcon(asset.category), asset.category, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(asset.name, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(asset.category, asset.sizeText, if (asset.compatible) "Compatible" else "Compatibility required")
                        .filter { it.isNotBlank() }.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PluginsScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        SectionHeader("AE Plugins")
        InfoBanner(
            "Plugin execution is capability-dependent. Part 5 classifies native binaries and keeps unsupported desktop plugins from being silently executed."
        )
        FeatureCard(Icons.Outlined.Extension, "Plugin library", "Browse plugin assets in the same Effects / Transitions / Nodes ecosystem.")
        FeatureCard(Icons.Filled.Build, "Compatibility", "ABI, platform, dependency and permission checks remain in PluginCompatibility / PluginDependencyManager.")
        FeatureCard(Icons.Filled.Effects, "Render bridge", "PluginRenderBridge and PluginShaderBridge connect compatible implementations to the compositor.")
        FeatureCard(Icons.Filled.Info, "Diagnostics", "DiagnosticsEngine reports why a plugin can or cannot execute.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { navigate(MotionStudioScreen.ASSETS) }) { Text("Asset Library") }
            OutlinedButton(onClick = callbacks.onRunDiagnostics) { Text("Run Diagnostics") }
        }
    }
}

@Composable
private fun ExportScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    var selected by remember { mutableStateOf(state.exportProfiles.firstOrNull()) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        outputUri = uri
        if (uri != null && selected != null) callbacks.onExport(selected!!, uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        SectionHeader("Export")
        if (state.project != null) {
            Text(
                "${state.project.name}  •  ${state.project.resolution}  •  ${state.project.fps}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        if (state.exportProfiles.isEmpty()) {
            EmptyCard("No export profiles", "Provide the real Part 6 ExportProfiles list to populate this screen.")
        } else {
            state.exportProfiles.forEach { profile ->
                ExportProfileCard(
                    profile = profile,
                    selected = selected?.id == profile.id,
                    onSelect = { selected = profile }
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val name = (selected?.name ?: "Motion Studio Export")
                        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                    createDocument.launch("$name.mp4")
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Export, "Export")
                Spacer(Modifier.width(7.dp))
                Text("Choose output and export")
            }
            if (outputUri != null) {
                Text("Output selected", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navigate(MotionStudioScreen.QUEUE) }) { Text("Open Render Queue") }
    }
}

@Composable
private fun ExportProfileCard(
    profile: UiExportProfile,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${profile.resolution} • ${profile.fps} • ${profile.codec} • ${profile.container} • ${profile.quality}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QueueScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        SectionHeader("Render Queue")
        if (state.renderProgress == null) {
            EmptyCard("Queue is idle", "Live RenderQueue jobs supplied by Part 6 will appear here.")
        } else {
            Text("Current export", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = state.renderProgress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            )
            Text("${(state.renderProgress * 100).toInt()}%")
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = callbacks.onCancelExport) { Text("Cancel") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { navigate(MotionStudioScreen.EXPORT) }) { Text("New Export") }
            OutlinedButton(onClick = { navigate(MotionStudioScreen.PERFORMANCE) }) { Text("Performance") }
        }
    }
}

@Composable
private fun PerformanceScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        SectionHeader("Performance & Recovery")
        MetricCard("Cache", state.cacheSizeText, Icons.Filled.Cached)
        MetricCard("Free storage", state.storageFreeText, Icons.Filled.Folder)
        MetricCard("Render monitor", "Part 6 PerformanceMonitor", Icons.Outlined.Memory)
        MetricCard("Memory manager", "Part 6 MemoryManager", Icons.Outlined.Memory)
        MetricCard("Adaptive preview", "Part 6 AdaptivePreview", Icons.Filled.Movie)
        Spacer(Modifier.height(8.dp))
        Button(onClick = callbacks.onClearCache) { Text("Clear managed cache") }
        OutlinedButton(onClick = callbacks.onRelinkMedia) { Text("Relink missing media") }
        OutlinedButton(onClick = callbacks.onRunDiagnostics) { Text("Run project diagnostics") }
        if (state.diagnostics.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Diagnostics", fontWeight = FontWeight.SemiBold)
            state.diagnostics.forEach {
                Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navigate(MotionStudioScreen.SETTINGS) }) { Text("Open Settings") }
    }
}

@Composable
private fun TutorialsScreen(
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    val lessons = listOf(
        "Getting started", "Projects & compositions", "Timeline & layers",
        "Keyframes & graph editor", "Masks & shapes", "Text animation",
        "Effects & transitions", "Nodes & compositing", "Audio workflow",
        "Color grading & scopes", "Tracking & stabilization", "Assets & plugins",
        "Export & render queue", "Performance & recovery"
    )
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Text("Motion Studio tutorials", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("The actual Part 1 TutorialContent/TutorialEngine can supply full lesson text and progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        items(lessons) { lesson ->
            Surface(
                Modifier.fillMaxWidth().clickable { },
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lesson, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.ArrowBack, "Open", modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            TextButton(onClick = { navigate(MotionStudioScreen.HOME) }) { Text("Back to Home") }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MotionStudioUiState,
    callbacks: MotionStudioUiCallbacks,
    navigate: (MotionStudioScreen) -> Unit
) {
    var autosave by remember { mutableStateOf(state.autosaveEnabled) }
    var adaptivePreview by remember { mutableStateOf(true) }
    var highQualityPreview by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        SectionHeader("Settings")
        SettingSwitch("Autosave", "Use the Part 1/6 project persistence boundaries.", autosave) { autosave = it }
        SettingSwitch("Adaptive preview", "Allow Part 6 to select a preview mode based on performance.", adaptivePreview) { adaptivePreview = it }
        SettingSwitch("High quality preview", "Prefer quality over preview performance when supported.", highQualityPreview) { highQualityPreview = it }

        Spacer(Modifier.height(10.dp))
        SectionHeader("Storage")
        MetricCard("Managed cache", state.cacheSizeText, Icons.Filled.Cached)
        MetricCard("Free storage", state.storageFreeText, Icons.Filled.Folder)
        OutlinedButton(onClick = callbacks.onClearCache) { Text("Clear cache") }

        Spacer(Modifier.height(10.dp))
        SectionHeader("Diagnostics")
        Button(onClick = callbacks.onRunDiagnostics) { Text("Run diagnostics") }

        Spacer(Modifier.height(10.dp))
        SectionHeader("About")
        Row(verticalAlignment = Alignment.CenterVertically) {
            MotionStudioLogo(58.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Motion Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Professional mobile editor UI", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = { navigate(MotionStudioScreen.TUTORIALS) }) { Text("Open tutorials") }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, title, modifier = Modifier.size(29.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FeatureCardSmall(icon: ImageVector, title: String) {
    Surface(
        Modifier.weight(1f),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, title)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, title)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, "Info", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(9.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyInspector(message: String) {
    Box(
        Modifier.fillMaxWidth().height(150.dp).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MotionStudioLogo(46.dp)
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun screenTitle(screen: MotionStudioScreen): String = when (screen) {
    MotionStudioScreen.HOME -> "Motion Studio"
    MotionStudioScreen.EDITOR -> "Editor"
    MotionStudioScreen.EFFECTS -> "Effects"
    MotionStudioScreen.TRANSITIONS -> "Transitions"
    MotionStudioScreen.NODES -> "Nodes / Graph"
    MotionStudioScreen.AUDIO -> "Audio"
    MotionStudioScreen.COLOR -> "Color"
    MotionStudioScreen.TRACKING -> "Tracking / AI"
    MotionStudioScreen.ASSETS -> "Asset Library"
    MotionStudioScreen.PLUGINS -> "AE Plugins"
    MotionStudioScreen.EXPORT -> "Export"
    MotionStudioScreen.QUEUE -> "Render Queue"
    MotionStudioScreen.PERFORMANCE -> "Performance"
    MotionStudioScreen.TUTORIALS -> "Tutorials"
    MotionStudioScreen.SETTINGS -> "Settings"
}

private fun layerIcon(kind: String): ImageVector = when (kind.uppercase()) {
    "VIDEO" -> Icons.Filled.Movie
    "IMAGE" -> Icons.Filled.Image
    "AUDIO" -> Icons.Filled.MusicNote
    "TEXT" -> Icons.Filled.TextFields
    "SHAPE" -> Icons.Filled.Dashboard
    "CAMERA" -> Icons.Filled.CameraAlt
    else -> Icons.Filled.Layers
}

private fun assetIcon(category: String): ImageVector = when (category.uppercase()) {
    "MEDIA", "VIDEO" -> Icons.Filled.Movie
    "AUDIO" -> Icons.Filled.MusicNote
    "PLUGIN", "AE_ASSET" -> Icons.Outlined.Extension
    "LUT", "COLOR" -> Icons.Filled.ColorLens
    "FONT" -> Icons.Filled.TextFields
    "EFFECT" -> Icons.Filled.Effects
    else -> Icons.Filled.Folder
}

private fun formatTime(us: Long): String {
    val safe = us.coerceAtLeast(0L)
    val totalMs = safe / 1_000L
    val minutes = totalMs / 60_000L
    val seconds = (totalMs / 1_000L) % 60L
    val millis = totalMs % 1_000L
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}

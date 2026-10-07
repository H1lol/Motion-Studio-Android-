package com.motionstudio.part1

enum class TutorialSection {
    WELCOME, PROJECTS, MEDIA, TIMELINE, LAYERS, KEYFRAMES, GRAPH_EDITOR,
    MASKS, EFFECTS, TRANSITIONS, NODES, COLOR, AUDIO, TEXT, SHAPES,
    THREE_D, TRACKING, EXPORT, PLUGINS, RECOVERY
}

data class TutorialStep(
    val id: String,
    val section: TutorialSection,
    val title: String,
    val body: String,
    val targetId: String? = null,
    val actionLabel: String = "Next"
)

class TutorialEngine {
    private val steps = listOf(
        TutorialStep("welcome", TutorialSection.WELCOME, "Welcome to Motion Studio",
            "Motion Studio combines editing, compositing, animation, effects, color and audio."),
        TutorialStep("projects", TutorialSection.PROJECTS, "Projects",
            "Create, open, duplicate, rename, recover and autosave projects."),
        TutorialStep("media", TutorialSection.MEDIA, "Media",
            "Import video, photos, audio, image sequences, fonts and supported project assets."),
        TutorialStep("timeline", TutorialSection.TIMELINE, "Timeline",
            "Trim, split, move, ripple and organize clips and layers."),
        TutorialStep("layers", TutorialSection.LAYERS, "Layers",
            "Reorder, hide, lock, solo, rename and group layers."),
        TutorialStep("keyframes", TutorialSection.KEYFRAMES, "Keyframes",
            "Animate transforms, opacity, colors, effects and custom parameters."),
        TutorialStep("graphs", TutorialSection.GRAPH_EDITOR, "Graph Editor",
            "Shape timing with Bezier easing and animation curves."),
        TutorialStep("masks", TutorialSection.MASKS, "Masks",
            "Create, feather, expand, invert and animate masks."),
        TutorialStep("effects", TutorialSection.EFFECTS, "Effects",
            "Apply effects non-destructively and animate their parameters."),
        TutorialStep("transitions", TutorialSection.TRANSITIONS, "Transitions",
            "Place and control transitions between compatible clips."),
        TutorialStep("nodes", TutorialSection.NODES, "Node Compositor",
            "Build explicit image-processing graphs."),
        TutorialStep("color", TutorialSection.COLOR, "Color",
            "Use exposure, contrast, temperature, saturation, curves and LUT workflows."),
        TutorialStep("audio", TutorialSection.AUDIO, "Audio",
            "Trim, gain, pan, fades, waveform analysis and keyframed audio."),
        TutorialStep("text", TutorialSection.TEXT, "Text",
            "Create styled text layers with animation and layout."),
        TutorialStep("shapes", TutorialSection.SHAPES, "Shapes",
            "Create vector-style paths and procedural shapes."),
        TutorialStep("3d", TutorialSection.THREE_D, "3D",
            "Use camera, transform and light data where the renderer supports it."),
        TutorialStep("tracking", TutorialSection.TRACKING, "Tracking",
            "Track visual features and apply motion data to layers."),
        TutorialStep("export", TutorialSection.EXPORT, "Export",
            "Choose dimensions, frame rate, codec, bitrate and audio settings."),
        TutorialStep("plugins", TutorialSection.PLUGINS, "Plugins and Assets",
            "Catalog AE/Motion assets and show compatibility instead of pretending every binary executes natively."),
        TutorialStep("recovery", TutorialSection.RECOVERY, "Recovery",
            "Use autosave and checkpoints after crashes or interrupted renders.")
    )

    var index: Int = 0
        private set

    fun current() = steps[index]
    fun next() = steps.getOrElse(++index) { steps.last() }.also { index = (index).coerceAtMost(steps.lastIndex) }
    fun previous() = steps.getOrElse(--index) { steps.first() }.also { index = index.coerceAtLeast(0) }
    fun restart() { index = 0 }
    fun complete() = index == steps.lastIndex
    fun all() = steps.toList()
}

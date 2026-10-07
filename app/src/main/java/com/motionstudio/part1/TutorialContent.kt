package com.motionstudio.part1

object TutorialContent {
    val quickStart = listOf(
        "Create a project and choose canvas size and frame rate.",
        "Import video, photos and audio.",
        "Place media on the timeline.",
        "Trim and split clips.",
        "Add layers, effects and transitions.",
        "Animate properties with keyframes.",
        "Use the graph editor for timing.",
        "Composite with masks and nodes.",
        "Grade the image and mix audio.",
        "Preview, render and export."
    )

    val editing = listOf(
        "Select a clip before applying clip-specific commands.",
        "Split divides a clip at the playhead.",
        "Trim changes in/out points without deleting the original source.",
        "Ripple edits move later timeline content when appropriate.",
        "Lock important layers to prevent accidental edits."
    )

    val animation = listOf(
        "A keyframe stores a value at a timeline time.",
        "Interpolation controls the change between keyframes.",
        "Bezier easing controls acceleration and deceleration.",
        "Transform properties can be animated independently.",
        "Effects and custom parameters can use the same keyframe system."
    )

    val compositing = listOf(
        "Layers are composited from back to front.",
        "Masks restrict where a layer contributes.",
        "Blend modes determine source/destination pixel interaction.",
        "Node graphs make processing order explicit.",
        "Precompositions allow complex sections to be reused."
    )

    val professionalWorkflow = listOf(
        "Organize source media before complex editing.",
        "Use proxies or reduced preview quality for heavy projects.",
        "Keep color management consistent from import through export.",
        "Cache expensive effects and invalidate dependent results.",
        "Use autosave and recovery checkpoints.",
        "Verify frame rate, dimensions and audio before final export."
    )
}

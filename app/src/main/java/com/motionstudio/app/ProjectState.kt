package com.motionstudio.app

import android.os.Handler
import android.os.Looper

/**
 * ProjectState — the single source of truth for the entire project.
 *
 * Every mutation in the app goes through this object. Nothing else holds
 * project data. Views observe it via listeners. Undo/redo is done by
 * serializing snapshots through a settable codec (ProjectSerializer at
 * runtime), which avoids a circular dependency between state and serializer.
 */
class ProjectState {

    // -------------------------------------------------------------------------
    // Project metadata
    // -------------------------------------------------------------------------
    var fps: Int = 30
    var width: Int = 1920
    var height: Int = 1080
    var playheadMs: Long = 0L
    var isPlaying: Boolean = false

    // -------------------------------------------------------------------------
    // Content lists
    // -------------------------------------------------------------------------
    val assets: MutableList<MediaAsset> = mutableListOf()
    val layers: MutableList<Layer2D> = mutableListOf()
    val textLayers: MutableList<TextLayer> = mutableListOf()
    val overlays: MutableList<OverlaySpec> = mutableListOf()
    val beatMarkers: MutableList<BeatMarker> = mutableListOf()
    val userMarkers: MutableList<UserMarker> = mutableListOf()
    val transitions: MutableList<TransitionPlacement> = mutableListOf()
    val trackStates: MutableList<TrackState> = mutableListOf()

    // -------------------------------------------------------------------------
    // ID counters — monotonic, never reused within a session
    // -------------------------------------------------------------------------
    var nextAssetId: Long = 1L
    var nextLayerId: Long = 1L
    var nextTextLayerId: Long = 1L
    var nextOverlayId: Long = 1L
    var nextEffectId: Long = 1L
    var nextTransitionId: Long = 1L
    var nextMarkerId: Long = 1L

    // -------------------------------------------------------------------------
    // Listener plumbing
    // -------------------------------------------------------------------------
    interface Listener {
        fun onProjectChanged()
    }

    private val listeners: MutableList<Listener> = mutableListOf()

    fun addListener(l: Listener) {
        if (l !in listeners) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    /**
     * Called after any mutation. Iterates listeners on the main thread so
     * views can safely update without extra dispatching.
     */
    fun notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.toList().forEach { it.onProjectChanged() }
        } else {
            Handler(Looper.getMainLooper()).post {
                listeners.toList().forEach { it.onProjectChanged() }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Undo / redo via snapshots
    // -------------------------------------------------------------------------

    /**
     * Snapshot codec. Set by MainActivity at startup (delegates to
     * ProjectSerializer). Until it's set, undo/redo silently no-ops.
     */
    interface SnapshotCodec {
        fun encode(state: ProjectState): String
        fun decode(json: String, into: ProjectState)
    }

    var codec: SnapshotCodec? = null

    private val undoStack: ArrayDeque<String> = ArrayDeque()
    private val redoStack: ArrayDeque<String> = ArrayDeque()

    private val maxUndo: Int = 40
    private var restoring: Boolean = false

    fun pushUndo() {
        if (restoring) return
        val c = codec ?: return
        try {
            undoStack.addLast(c.encode(this))
            while (undoStack.size > maxUndo) undoStack.removeFirst()
            redoStack.clear()
        } catch (_: Throwable) {
            // Skip snapshot rather than crash the UI thread.
        }
    }

    fun undo() {
        val c = codec ?: return
        val snap = undoStack.removeLastOrNull() ?: return
        try {
            redoStack.addLast(c.encode(this))
            restoring = true
            c.decode(snap, this)
        } catch (_: Throwable) {
        } finally {
            restoring = false
        }
        notifyChanged()
    }

    fun redo() {
        val c = codec ?: return
        val snap = redoStack.removeLastOrNull() ?: return
        try {
            undoStack.addLast(c.encode(this))
            restoring = true
            c.decode(snap, this)
        } catch (_: Throwable) {
        } finally {
            restoring = false
        }
        notifyChanged()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    // -------------------------------------------------------------------------
    // Reset — wipes everything back to a fresh project
    // -------------------------------------------------------------------------
    fun reset() {
        clearHistory()
        assets.clear()
        layers.clear()
        textLayers.clear()
        overlays.clear()
        beatMarkers.clear()
        userMarkers.clear()
        transitions.clear()
        trackStates.clear()

        nextAssetId = 1L
        nextLayerId = 1L
        nextTextLayerId = 1L
        nextOverlayId = 1L
        nextEffectId = 1L
        nextTransitionId = 1L
        nextMarkerId = 1L

        fps = 30
        width = 1920
        height = 1080
        playheadMs = 0L
        isPlaying = false

        seedDefaultTracks()
        notifyChanged()
    }

    // -------------------------------------------------------------------------
    // Default track layout — 4 video, 1 text, 2 audio
    // -------------------------------------------------------------------------
    private fun seedDefaultTracks() {
        repeat(4) { i ->
            trackStates.add(
                TrackState(
                    name = "V${i + 1}",
                    kind = TrackKind.VIDEO,
                )
            )
        }
        trackStates.add(TrackState(name = "T1", kind = TrackKind.TEXT))
        trackStates.add(TrackState(name = "A1", kind = TrackKind.AUDIO))
        trackStates.add(TrackState(name = "A2", kind = TrackKind.AUDIO))
    }

    init {
        seedDefaultTracks()
    }

    // -------------------------------------------------------------------------
    // Convenience lookups — read-only helpers for views
    // -------------------------------------------------------------------------
    fun layerById(id: Long): Layer2D? = layers.firstOrNull { it.id == id }
    fun textLayerById(id: Long): TextLayer? = textLayers.firstOrNull { it.id == id }
    fun assetById(id: Long): MediaAsset? = assets.firstOrNull { it.id == id }

    fun totalDurationMs(): Long {
        val layerEnd = layers.maxOfOrNull { it.timelineEndMs() } ?: 0L
        val textEnd = textLayers.maxOfOrNull { it.timelineEndMs } ?: 0L
        return maxOf(layerEnd, textEnd)
    }
}

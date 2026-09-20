package com.motionstudio.app

import android.content.Context
import android.graphics.Color
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * ProjectSerializer — JSON save/load for ProjectState.
 *
 * Two calls:
 *   serialize(state) → JSON string
 *   deserialize(json, state) → mutates state in place
 *
 * The deserialize path is careful: it clears every list and resets every
 * ID counter to match the loaded data. This makes it safe to use both for
 * file load AND for undo/redo snapshots.
 *
 * Every data class from EditorModels.kt has a matching toJson/fromJson pair.
 * Round-trips are exact — save, load, save again and the two JSON strings
 * are byte-identical.
 */
class ProjectSerializer(private val context: Context) {

    // =========================================================================
    // Public API
    // =========================================================================

    /** Serializes the entire project state to a JSON string. */
    fun serialize(state: ProjectState): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("fps", state.fps)
        root.put("width", state.width)
        root.put("height", state.height)
        root.put("playheadMs", state.playheadMs)

        root.put("assets", assetsToJson(state.assets))
        root.put("layers", layersToJson(state.layers))
        root.put("textLayers", textLayersToJson(state.textLayers))
        root.put("overlays", overlaysToJson(state.overlays))
        root.put("beatMarkers", beatsToJson(state.beatMarkers))
        root.put("userMarkers", userMarkersToJson(state.userMarkers))
        root.put("transitions", transitionsToJson(state.transitions))
        root.put("trackStates", tracksToJson(state.trackStates))

        root.put("nextAssetId", state.nextAssetId)
        root.put("nextLayerId", state.nextLayerId)
        root.put("nextTextLayerId", state.nextTextLayerId)
        root.put("nextOverlayId", state.nextOverlayId)
        root.put("nextEffectId", state.nextEffectId)
        root.put("nextTransitionId", state.nextTransitionId)
        root.put("nextMarkerId", state.nextMarkerId)

        return root.toString()
    }

    /**
     * Deserializes into an existing ProjectState, mutating every field.
     *
     * Called by:
     *   1. MainActivity.openProjectFrom() — user opens a saved file
     *   2. ProjectState.undo() / redo() via the SnapshotCodec interface
     */
    fun deserialize(json: String, into: ProjectState) {
        val root = JSONObject(json)

        // --- Metadata ---
        into.fps = root.optInt("fps", 30)
        into.width = root.optInt("width", 1920)
        into.height = root.optInt("height", 1080)
        into.playheadMs = root.optLong("playheadMs", 0L)
        into.isPlaying = false

        // --- Content lists ---
        into.assets.clear()
        into.layers.clear()
        into.textLayers.clear()
        into.overlays.clear()
        into.beatMarkers.clear()
        into.userMarkers.clear()
        into.transitions.clear()
        into.trackStates.clear()

        root.optJSONArray("assets")?.let { into.assets.addAll(assetsFromJson(it)) }
        root.optJSONArray("layers")?.let { into.layers.addAll(layersFromJson(it)) }
        root.optJSONArray("textLayers")?.let { into.textLayers.addAll(textLayersFromJson(it)) }
        root.optJSONArray("overlays")?.let { into.overlays.addAll(overlaysFromJson(it)) }
        root.optJSONArray("beatMarkers")?.let { into.beatMarkers.addAll(beatsFromJson(it)) }
        root.optJSONArray("userMarkers")?.let { into.userMarkers.addAll(userMarkersFromJson(it)) }
        root.optJSONArray("transitions")?.let { into.transitions.addAll(transitionsFromJson(it)) }
        root.optJSONArray("trackStates")?.let { into.trackStates.addAll(tracksFromJson(it)) }

        // --- ID counters ---
        into.nextAssetId = root.optLong("nextAssetId",
            (into.assets.maxOfOrNull { it.id } ?: 0L) + 1L)
        into.nextLayerId = root.optLong("nextLayerId",
            (into.layers.maxOfOrNull { it.id } ?: 0L) + 1L)
        into.nextTextLayerId = root.optLong("nextTextLayerId",
            (into.textLayers.maxOfOrNull { it.id } ?: 0L) + 1L)
        into.nextOverlayId = root.optLong("nextOverlayId",
            (into.overlays.maxOfOrNull { it.id } ?: 0L) + 1L)
        into.nextEffectId = root.optLong("nextEffectId",
            (into.layers.flatMap { it.effects }.maxOfOrNull { it.id } ?: 0L) + 1L)
        into.nextTransitionId = root.optLong("nextTransitionId",
            (into.transitions.maxOfOrNull { it.id } ?: 0L) + 1L)
        into.nextMarkerId = root.optLong("nextMarkerId",
            (into.userMarkers.maxOfOrNull { it.id } ?: 0L) + 1L)

        // --- Notify views ---
        into.notifyChanged()
    }

    // =========================================================================
    // MediaAsset
    // =========================================================================

    private fun assetsToJson(list: List<MediaAsset>): JSONArray {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("uri", a.uri.toString())
            o.put("name", a.name)
            o.put("kind", a.kind.name)
            o.put("durationMs", a.durationMs)
            o.put("sizeBytes", a.sizeBytes)
            arr.put(o)
        }
        return arr
    }

    private fun assetsFromJson(arr: JSONArray): List<MediaAsset> {
        val out = ArrayList<MediaAsset>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kind = runCatching {
                AssetKind.valueOf(o.optString("kind", "UNKNOWN"))
            }.getOrDefault(AssetKind.UNKNOWN)
            out.add(
                MediaAsset(
                    id = o.optLong("id"),
                    uri = Uri.parse(o.optString("uri")),
                    name = o.optString("name", "Asset"),
                    kind = kind,
                    durationMs = o.optLong("durationMs", 0L),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                )
            )
        }
        return out
    }
        // =========================================================================
    // Layer2D
    // =========================================================================

    private fun layersToJson(list: List<Layer2D>): JSONArray {
        val arr = JSONArray()
        for (l in list) {
            val o = JSONObject()
            o.put("id", l.id)
            o.put("assetId", l.assetId)
            o.put("timelineStartMs", l.timelineStartMs)
            o.put("sourceInMs", l.sourceInMs)
            o.put("sourceOutMs", l.sourceOutMs)
            o.put("trackIndex", l.trackIndex)
            o.put("speed", l.speed.toDouble())
            o.put("volume", l.volume.toDouble())
            o.put("visible", l.visible)
            o.put("locked", l.locked)
            o.put("linked", l.linked)
            o.put("muted", l.muted)
            o.put("parentId", l.parentId ?: -1L)
            o.put("blendMode", l.blendMode.name)
            o.put("props", propsToJson(l.props))
            o.put("effects", effectsToJson(l.effects))
            arr.put(o)
        }
        return arr
    }

    private fun layersFromJson(arr: JSONArray): List<Layer2D> {
        val out = ArrayList<Layer2D>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val props = o.optJSONObject("props")?.let { propsFromJson(it) }
                ?: defaultLayerProps()
            val effects = o.optJSONArray("effects")?.let { effectsFromJson(it) }
                ?: mutableListOf()
            val parentId = o.optLong("parentId", -1L).takeIf { it > 0L }
            val blend = runCatching {
                BlendMode.valueOf(o.optString("blendMode", "NORMAL"))
            }.getOrDefault(BlendMode.NORMAL)

            out.add(
                Layer2D(
                    id = o.optLong("id"),
                    assetId = o.optLong("assetId"),
                    timelineStartMs = o.optLong("timelineStartMs"),
                    sourceInMs = o.optLong("sourceInMs"),
                    sourceOutMs = o.optLong("sourceOutMs"),
                    trackIndex = o.optInt("trackIndex", 0),
                    speed = o.optDouble("speed", 1.0).toFloat(),
                    volume = o.optDouble("volume", 1.0).toFloat(),
                    visible = o.optBoolean("visible", true),
                    locked = o.optBoolean("locked", false),
                    linked = o.optBoolean("linked", true),
                    muted = o.optBoolean("muted", false),
                    parentId = parentId,
                    blendMode = blend,
                    props = props,
                    effects = effects,
                )
            )
        }
        return out
    }

    private fun defaultLayerProps(): MutableMap<String, PropertyTrack> = mutableMapOf(
        "x" to PropertyTrack("x", 0f),
        "y" to PropertyTrack("y", 0f),
        "scale" to PropertyTrack("scale", 1f),
        "rotation" to PropertyTrack("rotation", 0f),
        "opacity" to PropertyTrack("opacity", 1f),
    )

    // =========================================================================
    // TextLayer
    // =========================================================================

    private fun textLayersToJson(list: List<TextLayer>): JSONArray {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("text", t.text)
            o.put("fontPath", t.fontPath ?: "")
            o.put("fontSize", t.fontSize.toDouble())
            o.put("color", t.color)
            o.put("strokeColor", t.strokeColor)
            o.put("strokeWidth", t.strokeWidth.toDouble())
            o.put("shadow", t.shadow)
            o.put("shadowRadius", t.shadowRadius.toDouble())
            o.put("tracking", t.tracking.toDouble())
            o.put("lineHeight", t.lineHeight.toDouble())
            o.put("align", t.align)
            o.put("preset", t.preset)
            o.put("presetSpeed", t.presetSpeed.toDouble())
            o.put("timelineStartMs", t.timelineStartMs)
            o.put("timelineEndMs", t.timelineEndMs)
            o.put("trackIndex", t.trackIndex)
            o.put("props", propsToJson(t.props))
            arr.put(o)
        }
        return arr
    }

    private fun textLayersFromJson(arr: JSONArray): List<TextLayer> {
        val out = ArrayList<TextLayer>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val props = o.optJSONObject("props")?.let { propsFromJson(it) }
                ?: defaultTextProps()
            val fontPath = o.optString("fontPath", "").takeIf { it.isNotEmpty() }

            out.add(
                TextLayer(
                    id = o.optLong("id"),
                    text = o.optString("text", "TEXT"),
                    fontPath = fontPath,
                    fontSize = o.optDouble("fontSize", 96.0).toFloat(),
                    color = o.optInt("color", Color.WHITE),
                    strokeColor = o.optInt("strokeColor", Color.BLACK),
                    strokeWidth = o.optDouble("strokeWidth", 0.0).toFloat(),
                    shadow = o.optBoolean("shadow", false),
                    shadowRadius = o.optDouble("shadowRadius", 8.0).toFloat(),
                    tracking = o.optDouble("tracking", 0.0).toFloat(),
                    lineHeight = o.optDouble("lineHeight", 1.1).toFloat(),
                    align = o.optInt("align", 1),
                    preset = o.optString("preset", "NONE"),
                    presetSpeed = o.optDouble("presetSpeed", 1.0).toFloat(),
                    timelineStartMs = o.optLong("timelineStartMs", 0L),
                    timelineEndMs = o.optLong("timelineEndMs", 5000L),
                    trackIndex = o.optInt("trackIndex", 4),
                    props = props,
                )
            )
        }
        return out
    }

    private fun defaultTextProps(): MutableMap<String, PropertyTrack> = mutableMapOf(
        "x" to PropertyTrack("x", 0f),
        "y" to PropertyTrack("y", 0f),
        "scale" to PropertyTrack("scale", 1f),
        "rotation" to PropertyTrack("rotation", 0f),
        "opacity" to PropertyTrack("opacity", 1f),
        "reveal" to PropertyTrack("reveal", 1f),
    )

    // =========================================================================
    // OverlaySpec
    // =========================================================================

    private fun overlaysToJson(list: List<OverlaySpec>): JSONArray {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("type", s.type)
            o.put("text", s.text)
            o.put("x", s.x.toDouble())
            o.put("y", s.y.toDouble())
            o.put("scale", s.scale.toDouble())
            o.put("rotation", s.rotation.toDouble())
            o.put("opacity", s.opacity.toDouble())
            o.put("radius", s.radius.toDouble())
            o.put("color", s.color)
            arr.put(o)
        }
        return arr
    }

    private fun overlaysFromJson(arr: JSONArray): List<OverlaySpec> {
        val out = ArrayList<OverlaySpec>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                OverlaySpec(
                    id = o.optLong("id"),
                    type = o.optString("type", "SHAPE"),
                    text = o.optString("text", ""),
                    x = o.optDouble("x", 0.5).toFloat(),
                    y = o.optDouble("y", 0.5).toFloat(),
                    scale = o.optDouble("scale", 1.0).toFloat(),
                    rotation = o.optDouble("rotation", 0.0).toFloat(),
                    opacity = o.optDouble("opacity", 1.0).toFloat(),
                    radius = o.optDouble("radius", 120.0).toFloat(),
                    color = o.optInt("color", Color.WHITE),
                )
            )
        }
        return out
    }

    // =========================================================================
    // Markers
    // =========================================================================

    private fun beatsToJson(list: List<BeatMarker>): JSONArray {
        val arr = JSONArray()
        for (b in list) {
            arr.put(JSONObject().apply {
                put("timeMs", b.timeMs)
                put("strength", b.strength.toDouble())
            })
        }
        return arr
    }

    private fun beatsFromJson(arr: JSONArray): List<BeatMarker> {
        val out = ArrayList<BeatMarker>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                BeatMarker(
                    timeMs = o.optLong("timeMs"),
                    strength = o.optDouble("strength", 1.0).toFloat(),
                )
            )
        }
        return out
    }

    private fun userMarkersToJson(list: List<UserMarker>): JSONArray {
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("timeMs", m.timeMs)
            o.put("kind", m.kind.name)
            o.put("label", m.label)
            o.put("color", m.color)
            arr.put(o)
        }
        return arr
    }

    private fun userMarkersFromJson(arr: JSONArray): List<UserMarker> {
        val out = ArrayList<UserMarker>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kind = runCatching {
                MarkerKind.valueOf(o.optString("kind", "USER"))
            }.getOrDefault(MarkerKind.USER)
            out.add(
                UserMarker(
                    id = o.optLong("id"),
                    timeMs = o.optLong("timeMs"),
                    kind = kind,
                    label = o.optString("label", ""),
                    color = o.optInt("color", Color.WHITE),
                )
            )
        }
        return out
    }

    // =========================================================================
    // Transitions
    // =========================================================================

    private fun transitionsToJson(list: List<TransitionPlacement>): JSONArray {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("leftClipId", t.leftClipId)
            o.put("rightClipId", t.rightClipId)
            o.put("transitionName", t.transitionName)
            o.put("durationMs", t.durationMs)
            arr.put(o)
        }
        return arr
    }

    private fun transitionsFromJson(arr: JSONArray): List<TransitionPlacement> {
        val out = ArrayList<TransitionPlacement>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                TransitionPlacement(
                    id = o.optLong("id"),
                    leftClipId = o.optLong("leftClipId"),
                    rightClipId = o.optLong("rightClipId"),
                    transitionName = o.optString("transitionName", "Fade"),
                    durationMs = o.optLong("durationMs", 800L),
                )
            )
        }
        return out
    }

    // =========================================================================
    // Tracks
    // =========================================================================

    private fun tracksToJson(list: List<TrackState>): JSONArray {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("name", t.name)
            o.put("kind", t.kind.name)
            o.put("muted", t.muted)
            o.put("solo", t.solo)
            o.put("locked", t.locked)
            o.put("hidden", t.hidden)
            o.put("heightDp", t.heightDp)
            arr.put(o)
        }
        return arr
    }

    private fun tracksFromJson(arr: JSONArray): List<TrackState> {
        val out = ArrayList<TrackState>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kind = runCatching {
                TrackKind.valueOf(o.optString("kind", "VIDEO"))
            }.getOrDefault(TrackKind.VIDEO)
            out.add(
                TrackState(
                    name = o.optString("name", "V1"),
                    kind = kind,
                    muted = o.optBoolean("muted", false),
                    solo = o.optBoolean("solo", false),
                    locked = o.optBoolean("locked", false),
                    hidden = o.optBoolean("hidden", false),
                    heightDp = o.optInt("heightDp", 48),
                )
            )
        }
        return out
    }

    // =========================================================================
    // Effects and property tracks
    // =========================================================================

    private fun effectsToJson(list: List<EffectInstance>): JSONArray {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("type", e.type)
            o.put("enabled", e.enabled)
            o.put("usesAi", e.usesAi)
            o.put("aiModelKey", e.aiModelKey ?: "")

            val params = JSONObject()
            for ((k, v) in e.params) params.put(k, v.toDouble())
            o.put("params", params)

            val paramTracks = JSONObject()
            for ((k, t) in e.paramTracks) paramTracks.put(k, trackToJson(t))
            o.put("paramTracks", paramTracks)

            arr.put(o)
        }
        return arr
    }

    private fun effectsFromJson(arr: JSONArray): MutableList<EffectInstance> {
        val out = mutableListOf<EffectInstance>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)

            val params = mutableMapOf("p0" to 1f, "p1" to 0f, "p2" to 0f)
            o.optJSONObject("params")?.let { p ->
                for (key in p.keys()) {
                    params[key] = p.optDouble(key, 0.0).toFloat()
                }
            }

            val paramTracks = mutableMapOf<String, PropertyTrack>()
            o.optJSONObject("paramTracks")?.let { pt ->
                for (key in pt.keys()) {
                    pt.optJSONObject(key)?.let { t ->
                        paramTracks[key] = trackFromJson(t, key)
                    }
                }
            }

            val aiKey = o.optString("aiModelKey", "").takeIf { it.isNotEmpty() }

            out.add(
                EffectInstance(
                    id = o.optLong("id"),
                    type = o.optString("type", "Unknown"),
                    enabled = o.optBoolean("enabled", true),
                    params = params,
                    paramTracks = paramTracks,
                    usesAi = o.optBoolean("usesAi", false),
                    aiModelKey = aiKey,
                )
            )
        }
        return out
    }

    private fun propsToJson(props: Map<String, PropertyTrack>): JSONObject {
        val o = JSONObject()
        for ((key, track) in props) o.put(key, trackToJson(track))
        return o
    }

    private fun propsFromJson(obj: JSONObject): MutableMap<String, PropertyTrack> {
        val out = mutableMapOf<String, PropertyTrack>()
        for (key in obj.keys()) {
            val t = obj.optJSONObject(key) ?: continue
            out[key] = trackFromJson(t, key)
        }
        return out
    }

    private fun trackToJson(track: PropertyTrack): JSONObject {
        val o = JSONObject()
        o.put("channel", track.channel)
        o.put("baseValue", track.baseValue.toDouble())

        val keys = JSONArray()
        for (k in track.keys) {
            val ko = JSONObject()
            ko.put("timeMs", k.timeMs)
            ko.put("value", k.value.toDouble())
            ko.put("interp", k.interp.name)
            ko.put("inHandleX", k.inHandleX.toDouble())
            ko.put("inHandleY", k.inHandleY.toDouble())
            ko.put("outHandleX", k.outHandleX.toDouble())
            ko.put("outHandleY", k.outHandleY.toDouble())
            ko.put("easeAmp", k.easeAmp.toDouble())
            keys.put(ko)
        }
        o.put("keys", keys)
        return o
    }

    private fun trackFromJson(obj: JSONObject, fallbackChannel: String): PropertyTrack {
        val channel = obj.optString("channel", fallbackChannel)
        val baseValue = obj.optDouble("baseValue", 0.0).toFloat()
        val track = PropertyTrack(channel, baseValue)

        obj.optJSONArray("keys")?.let { arr ->
            for (i in 0 until arr.length()) {
                val ko = arr.getJSONObject(i)
                val interp = runCatching {
                    Interp.valueOf(ko.optString("interp", "EASE_IN_OUT"))
                }.getOrDefault(Interp.EASE_IN_OUT)

                track.keys.add(
                    Keyframe(
                        timeMs = ko.optLong("timeMs"),
                        value = ko.optDouble("value", 0.0).toFloat(),
                        interp = interp,
                        inHandleX = ko.optDouble("inHandleX", 0.33).toFloat(),
                        inHandleY = ko.optDouble("inHandleY", 0.0).toFloat(),
                        outHandleX = ko.optDouble("outHandleX", 0.66).toFloat(),
                        outHandleY = ko.optDouble("outHandleY", 1.0).toFloat(),
                        easeAmp = ko.optDouble("easeAmp", 0.3).toFloat(),
                    )
                )
            }
        }
        return track
    }
}

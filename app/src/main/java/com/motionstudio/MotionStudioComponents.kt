package com.motionstudio.app

import com.motionstudio.part1.*
import com.motionstudio.part2.*
import com.motionstudio.part5.EffectParameter
import java.util.concurrent.CopyOnWriteArrayList

/** Shared application state. Engines remain the source of truth; this is the UI/application projection. */
data class MotionStudioApplicationState(
    val project: Project? = null,
    val compositionId: String? = null,
    val currentTimeUs: Long = 0L,
    val selectedLayerIds: Set<String> = emptySet(),
    val playing: Boolean = false,
    val diagnostics: List<String> = emptyList()
)

fun Project.toUiState(timeUs: Long, selected: Set<String>, playing: Boolean): com.motionstudio.ui.MotionStudioUiState {
    val comp = compositions.firstOrNull()
    return com.motionstudio.ui.MotionStudioUiState(
        project = com.motionstudio.ui.UiProject(id, name, comp?.let { "${it.width} × ${it.height}" } ?: "—", comp?.let { "${it.frameRate} fps" } ?: "—", comp?.let { formatTime(it.durationUs) } ?: "00:00:00"),
        projects = listOf(com.motionstudio.ui.UiProject(id, name)),
        layers = comp?.layers?.map { l -> com.motionstudio.ui.UiLayer(l.id,l.name,l.type.name,l.startUs,l.durationUs,l.visible,l.locked,l.opacity.toFloat(),l.keyframes.isNotEmpty(),l.effects.size) } ?: emptyList(),
        currentTimeUs = timeUs,
        compositionDurationUs = comp?.durationUs ?: 0L,
        selectedLayerId = selected.firstOrNull(),
        isPlaying = playing
    )
}

private fun formatTime(us: Long): String {
    val total = (us / 1_000_000L).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(total/3600,(total%3600)/60,total%60)
}

class MotionStudioEventBus {
    private val listeners = CopyOnWriteArrayList<(MotionStudioApplicationState) -> Unit>()
    fun subscribe(listener: (MotionStudioApplicationState) -> Unit): () -> Unit { listeners += listener; return { listeners -= listener } }
    fun publish(state: MotionStudioApplicationState) = listeners.forEach { it(state) }
}

/** Effect inspector model generated directly from Part 5 schemas. */
data class ParameterEditorState(val schema: EffectParameter, val value: Any?, val animated: Boolean = false)
class EffectInspectorModel {
    private val values = mutableMapOf<String, Any?>()
    private val animated = mutableSetOf<String>()
    fun bind(parameters: List<EffectParameter>) { parameters.forEach { values.putIfAbsent(it.id, it.default) } }
    fun set(id: String, value: Any?) { values[id] = value }
    fun toggleKeyframe(id: String, enabled: Boolean) { if (enabled) animated += id else animated -= id }
    fun editors(parameters: List<EffectParameter>) = parameters.map { ParameterEditorState(it, values[it.id] ?: it.default, it.id in animated) }
}

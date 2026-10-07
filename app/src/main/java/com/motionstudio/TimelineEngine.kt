package com.motionstudio.part1

data class TimelineSelection(
    val layerIds: Set<LayerId> = emptySet(),
    val playheadUs: Long = 0L
)

class TimelineEngine(private val composition: Composition) {
    var selection = TimelineSelection()
        private set

    fun selectLayer(id: LayerId) { selection = selection.copy(layerIds = setOf(id)) }
    fun clearSelection() { selection = selection.copy(layerIds = emptySet()) }
    fun setPlayhead(timeUs: Long) {
        selection = selection.copy(playheadUs = timeUs.coerceIn(0L, composition.durationUs))
    }

    fun splitSelectedAtPlayhead(): List<Layer> {
        val result = mutableListOf<Layer>()
        val t = selection.playheadUs
        for (layer in composition.layers.filter { it.id in selection.layerIds }) {
            val local = t - layer.startUs
            if (local <= 0 || local >= layer.durationUs) continue
            val second = layer.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${layer.name} (split)",
                startUs = t,
                durationUs = layer.durationUs - local
            )
            layer.durationUs = local
            result += second
        }
        composition.layers.addAll(result)
        composition.layers.sortBy { it.startUs }
        return result
    }

    fun moveSelected(deltaUs: Long) {
        composition.layers.filter { it.id in selection.layerIds && !it.locked }
            .forEach { it.startUs = (it.startUs + deltaUs).coerceAtLeast(0L) }
    }
}

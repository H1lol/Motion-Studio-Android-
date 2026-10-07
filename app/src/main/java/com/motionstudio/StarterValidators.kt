package com.motionstudio.part1

object ProjectValidator {
    fun validate(project: Project): List<String> {
        val errors = mutableListOf<String>()
        project.compositions.forEach { c ->
            if (c.width <= 0 || c.height <= 0) errors += "${c.name}: invalid dimensions"
            if (c.frameRate <= 0.0) errors += "${c.name}: invalid frame rate"
            if (c.durationUs < 0) errors += "${c.name}: invalid duration"

            val ids = mutableSetOf<String>()
            c.layers.forEach { layer ->
                if (!ids.add(layer.id)) errors += "${c.name}: duplicate layer id ${layer.id}"
                if (layer.durationUs < 0) errors += "${layer.name}: negative duration"
                if (layer.opacity !in 0.0..1.0) errors += "${layer.name}: opacity outside 0..1"
            }
        }
        return errors
    }
}

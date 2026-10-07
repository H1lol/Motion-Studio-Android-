package com.motionstudio.part1

import java.util.UUID

typealias ProjectId = String
typealias CompositionId = String
typealias LayerId = String
typealias AssetId = String
typealias KeyframeId = String

data class Project(
    val id: ProjectId = UUID.randomUUID().toString(),
    var name: String = "Untitled Project",
    var compositions: MutableList<Composition> = mutableListOf(),
    var assets: MutableList<MediaAsset> = mutableListOf(),
    var version: Int = 1
)

data class Composition(
    val id: CompositionId = UUID.randomUUID().toString(),
    var name: String = "Main Composition",
    var width: Int = 1920,
    var height: Int = 1080,
    var frameRate: Double = 30.0,
    var durationUs: Long = 10_000_000L,
    var layers: MutableList<Layer> = mutableListOf()
)

enum class LayerType { VIDEO, IMAGE, AUDIO, TEXT, SHAPE, NULL, PRECOMP, ADJUSTMENT, CAMERA, LIGHT }

data class Layer(
    val id: LayerId = UUID.randomUUID().toString(),
    var name: String,
    var type: LayerType,
    var startUs: Long = 0,
    var durationUs: Long = 1_000_000,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var opacity: Double = 1.0,
    var transform: Transform2D = Transform2D(),
    var keyframes: MutableList<Keyframe> = mutableListOf(),
    var effects: MutableList<EffectInstance> = mutableListOf()
)

data class Transform2D(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var scaleX: Double = 1.0,
    var scaleY: Double = 1.0,
    var rotationDeg: Double = 0.0,
    var anchorX: Double = 0.0,
    var anchorY: Double = 0.0
)

data class MediaAsset(
    val id: AssetId = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val durationUs: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class EffectInstance(
    val id: String = UUID.randomUUID().toString(),
    val effectId: String,
    var enabled: Boolean = true,
    var parameters: MutableMap<String, Double> = mutableMapOf()
)

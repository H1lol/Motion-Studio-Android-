package com.motionstudio.part2
enum class RenderBackendKind{CPU,OPENGL_ES,VULKAN,OTHER}
data class RendererCapabilities(val gpuAvailable:Boolean,val openGlEsMajor:Int,val vulkanAvailable:Boolean,val computeSupported:Boolean,val hdrSupported:Boolean,val maxTextureSize:Int,val backend:RenderBackendKind)

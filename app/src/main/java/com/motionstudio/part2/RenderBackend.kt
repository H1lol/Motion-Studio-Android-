package com.motionstudio.part2
interface RenderBackend {val capabilities:RendererCapabilities;fun render(source:FrameBuffer,graph:RenderGraph<FrameBuffer>?=null):FrameBuffer}
class CpuReferenceBackend:RenderBackend{override val capabilities=RendererCapabilities(false,0,false,false,false,16384,RenderBackendKind.CPU);override fun render(source:FrameBuffer,graph:RenderGraph<FrameBuffer>?):FrameBuffer=graph?.execute()?.values?.lastOrNull()?:source.copy()}
/** GPU adapters intentionally contain no fake implementation; Android EGL/Vulkan integration belongs in platform modules. */
interface GpuBackend:RenderBackend

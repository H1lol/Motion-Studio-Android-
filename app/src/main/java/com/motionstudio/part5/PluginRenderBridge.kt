package com.motionstudio.part5
data class PluginFrameContext(val inputTexture:Any?,val outputTexture:Any?,val frameTimeSeconds:Double,val compositionTimeSeconds:Double,val width:Int,val height:Int,val colorSpace:String,val alphaMode:String,val parameters:Map<String,Any?>,val quality:Float)
interface PluginRenderBridge{fun render(context:PluginFrameContext):Boolean}

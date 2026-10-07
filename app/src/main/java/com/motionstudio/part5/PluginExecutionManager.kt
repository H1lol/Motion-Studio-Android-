package com.motionstudio.part5
interface PluginExecutor{fun open():Boolean;fun render(request:PluginRenderRequest):PluginRenderResult;fun close()}
data class PluginRenderRequest(val input:Any?,val output:Any?,val timeSeconds:Double,val resolution:IntArray,val parameters:Map<String,Any?>)
data class PluginRenderResult(val success:Boolean,val output:Any?,val message:String)
class PluginExecutionManager{
 private val active=mutableMapOf<String,PluginExecutor>()
 fun attach(id:String,executor:PluginExecutor){require(id.isNotBlank());require(executor.open());active[id]=executor}
 fun render(id:String,request:PluginRenderRequest)=active[id]?.render(request)?:PluginRenderResult(false,null,"Plugin is not loaded")
 fun detach(id:String){active.remove(id)?.close()}
 fun clear(){active.values.forEach{runCatching{it.close()}};active.clear()}
}

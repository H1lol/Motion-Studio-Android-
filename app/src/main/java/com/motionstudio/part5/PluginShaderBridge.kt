package com.motionstudio.part5
data class ShaderPlugin(val id:String,val source:String,val language:String,val uniforms:Map<String,String>,val version:Int=1)
interface PluginShaderBridge{fun compile(shader:ShaderPlugin):Boolean;fun render(shaderId:String,input:Any?,output:Any?,uniforms:Map<String,Any?>):Boolean}

package com.motionstudio.part2
data class ShaderParameter(val name:String,val type:String,val defaultValue:String,val min:String?=null,val max:String?=null)
data class ShaderDescriptor(val id:String,val version:Int,val source:String,val parameters:List<ShaderParameter>)
class ShaderLibrary{private val shaders=mutableMapOf<String,ShaderDescriptor>();fun register(d:ShaderDescriptor){require(d.id.isNotBlank()&&d.version>0);shaders[d.id]=d};fun lookup(id:String)=shaders[id];fun require(id:String)=shaders[id]?:error("Shader not registered: $id");fun all()=shaders.values.sortedBy{it.id}}

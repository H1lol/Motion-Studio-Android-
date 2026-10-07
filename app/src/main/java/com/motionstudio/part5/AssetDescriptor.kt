package com.motionstudio.part5
data class AssetDescriptor(val id:String,val name:String,val filename:String,val extension:String,val category:AssetCategory,val version:String,val source:String,val hash:String,val size:Long,val dependencies:Set<String>,val compatibility:Compatibility,val executionMode:ExecutionMode,val parameters:List<EffectParameter> = emptyList(),val installed:Boolean=false)
enum class AssetCategory{EFFECT,TRANSITION,PRESET,TEMPLATE,NODE,MOTION_BRO,AE_ASSET,PLUGIN,SHADER,LUT,FONT,SCRIPT,MEDIA,NATIVE_EXTENSION,PACKAGE,UNKNOWN}
enum class ExecutionMode{NATIVE,GPU,SHADER,PORTABLE,COMPATIBILITY,EXTERNAL_RENDERER,REBUILD_REQUIRED,UNSUPPORTED}
data class Compatibility(val platform:String,val architecture:String,val compatible:Boolean,val reason:String)

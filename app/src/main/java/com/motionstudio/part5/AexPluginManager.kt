package com.motionstudio.part5
import java.io.File
class AexPluginManager(private val registry:AssetRegistry){
 fun inspect(file:File):AssetDescriptor{val info=PluginBinaryLoader.inspect(file);val cr=PluginCompatibility.classifyResult(info);val compat=cr.compatibility;val a=AssetDescriptor(info.hash,file.nameWithoutExtension,file.name,file.extension,AssetCategory.PLUGIN,info.version,"import",info.hash,file.length(),emptySet(),compat,cr.mode);registry.register(a);return a}
}

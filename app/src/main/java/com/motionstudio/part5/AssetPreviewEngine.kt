package com.motionstudio.part5
import java.io.File
class AssetPreviewEngine(private val cache:File){
 init{cache.mkdirs()}
 fun previewKey(asset:AssetDescriptor,parameterHash:String="")="${asset.hash}_$parameterHash"
 fun cached(asset:AssetDescriptor,parameterHash:String="")=File(cache,previewKey(asset,parameterHash)+".bin").takeIf{it.isFile}
 fun store(asset:AssetDescriptor,bytes:ByteArray,parameterHash:String=""){File(cache,previewKey(asset,parameterHash)+".bin").writeBytes(bytes)}
 fun canPreview(asset:AssetDescriptor)=asset.executionMode!=ExecutionMode.NATIVE||asset.category!=AssetCategory.PLUGIN
}

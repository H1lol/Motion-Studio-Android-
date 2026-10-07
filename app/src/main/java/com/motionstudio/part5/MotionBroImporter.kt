package com.motionstudio.part5
import java.io.File
data class ImportedCatalog(val assets:List<AssetDescriptor>,val resources:List<File>,val warnings:List<String>)
class MotionBroImporter(private val registry:AssetRegistry){
 fun scan(root:File):ImportedCatalog{require(root.exists());val warnings=mutableListOf<String>();val files=root.walkTopDown().filter{it.isFile}.toList();val assets=files.mapNotNull{f->val c=FormatRegistry.category(f);if(c==AssetCategory.UNKNOWN)null else AssetDescriptor(f.absolutePath,f.nameWithoutExtension,f.name,f.extension,c,"unknown","MotionBro",sha(f),f.length(),emptySet(),Compatibility("unknown","unknown",true,"metadata scan only"),ExecutionMode.REBUILD_REQUIRED)};assets.forEach(registry::register);return ImportedCatalog(assets,files,warnings)}
 private fun sha(f:File)=f.inputStream().use{java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString(""){b->"%02x".format(b)}}
}

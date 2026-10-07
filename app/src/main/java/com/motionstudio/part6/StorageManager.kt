package com.motionstudio.part6
import java.io.File
enum class StorageKind{PROJECT,CACHE,PROXY,ASSET,PLUGIN,TEMP}
data class StorageAccount(val kind:StorageKind,val bytes:Long)
class StorageManager(private val root:File){
 init{root.mkdirs()}
 private fun dir(k:StorageKind)=File(root,k.name.lowercase()).apply{mkdirs()}
 fun file(k:StorageKind,name:String)=File(dir(k),name)
 fun freeSpace():Long=root.usableSpace
 fun account():List<StorageAccount> = StorageKind.values().map{k->StorageAccount(k,dir(k).walkTopDown().filter{it.isFile}.sumOf{it.length()})}
 fun totalUsage()=account().sumOf{it.bytes}
 fun hasSpace(required:Long,reserve:Long=0)=required>=0&&freeSpace()>=required+reserve
 fun cleanup(kind:StorageKind,maxBytes:Long):Long{require(maxBytes>=0);var usage=dir(kind).walkTopDown().filter{it.isFile}.sumOf{it.length()};if(usage<=maxBytes)return 0;var freed=0L;dir(kind).walkTopDown().filter{it.isFile}.sortedBy{it.lastModified()}.forEach{if(usage>maxBytes){val n=it.length();if(it.delete()){usage-=n;freed+=n}}};return freed}
 fun createTemp(prefix:String="render"):File=File.createTempFile(prefix,".tmp",dir(StorageKind.TEMP))
 fun deleteTemp(f:File):Boolean{require(f.absoluteFile.parentFile==dir(StorageKind.TEMP).absoluteFile);return !f.exists()||f.delete()}
}

package com.motionstudio.part6
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

enum class CacheKind{MEMORY,DISK,RENDER,THUMBNAIL,WAVEFORM,PROXY,SHADER}
data class CacheEntry(val key:String,val file:File?,val bytes:Long,val createdAt:Long,val validated:Boolean)
class CacheEngine(private val root:File,private val memoryLimitBytes:Long=128L*1024*1024,private val diskLimitBytes:Long=2L*1024*1024*1024){private val mem=object:LinkedHashMap<String,ByteArray>(16,.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,ByteArray>)=memoryBytes>memoryLimitBytes};private var memoryBytes=0L
 init{root.mkdirs()}
 @Synchronized fun putMemory(key:String,data:ByteArray){mem.remove(key)?.let{memoryBytes-=it.size};mem[key]=data.copyOf();memoryBytes+=data.size;trimMemory()};@Synchronized fun getMemory(key:String)=mem[key]?.copyOf();private fun trimMemory(){val i=mem.entries.iterator();while(memoryBytes>memoryLimitBytes&&i.hasNext()){memoryBytes-=i.next().value.size;i.remove()}}
 fun path(kind:CacheKind,key:String)=File(File(root,kind.name.lowercase()),sha(key));fun put(kind:CacheKind,key:String,data:ByteArray):File{require(kind!=CacheKind.MEMORY);val f=path(kind,key);f.parentFile!!.mkdirs();val tmp=File(f.parentFile,f.name+".tmp");tmp.writeBytes(data);require(tmp.renameTo(f));trimDisk();return f};fun get(kind:CacheKind,key:String):ByteArray?=path(kind,key).takeIf{it.isFile}?.readBytes();fun remove(kind:CacheKind,key:String)=path(kind,key).delete();fun clear(kind:CacheKind?=null){if(kind==null)root.listFiles()?.forEach{it.deleteRecursively()}else File(root,kind.name.lowercase()).deleteRecursively()};fun usage()=root.walkTopDown().filter{it.isFile}.sumOf{it.length()};private fun trimDisk(){while(usage()>diskLimitBytes){val oldest=root.walkTopDown().filter{it.isFile}.minByOrNull{it.lastModified()}?:break;oldest.delete()}};private fun sha(s:String):String=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
}

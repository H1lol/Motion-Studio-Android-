package com.motionstudio.part4
import java.io.*
import java.security.MessageDigest
class MediaCache(private val dir:File,private val maxBytes:Long){init{dir.mkdirs();require(maxBytes>0)}
 @Synchronized fun put(key:String,data:ByteArray){val f=file(key);f.parentFile?.mkdirs();f.writeBytes(data);evict()}
 @Synchronized fun get(key:String):ByteArray?=file(key).takeIf{it.isFile}?.let{it.setLastModified(System.currentTimeMillis());it.readBytes()}
 @Synchronized fun remove(key:String){file(key).delete()}
 @Synchronized fun clear(){dir.listFiles()?.forEach{it.deleteRecursively()}}
 private fun file(k:String)=File(dir,sha(k));private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
 private fun evict(){var total=dir.walkTopDown().filter{it.isFile}.sumOf{it.length()};while(total>maxBytes){val victim=dir.walkTopDown().filter{it.isFile}.minByOrNull{it.lastModified()}?:break;total-=victim.length();victim.delete()}}
}

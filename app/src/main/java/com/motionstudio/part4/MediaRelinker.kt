package com.motionstudio.part4
import java.io.File
import java.security.MessageDigest
data class RelinkCandidate(val file:File,val score:Int)
object MediaRelinker{
 fun find(root:File,missing:String,expectedSize:Long?=null,expectedHash:String?=null):List<RelinkCandidate>{require(root.exists());val name=File(missing).name.lowercase();return root.walkTopDown().filter{it.isFile}.map{f->var s=0;if(f.name.lowercase()==name)s+=100;if(expectedSize!=null&&f.length()==expectedSize)s+=20;if(expectedHash!=null&&hash(f)==expectedHash)s+=200;RelinkCandidate(f,s)}.filter{it.score>0}.sortedByDescending{it.score}.take(100).toList()}
 private fun hash(f:File):String{val md=MessageDigest.getInstance("SHA-256");f.inputStream().use{i->val b=ByteArray(1 shl 16);while(true){val n=i.read(b);if(n<0)break;md.update(b,0,n)}};return md.digest().joinToString(""){"%02x".format(it)}}
}

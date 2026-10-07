package com.motionstudio.part6
import java.io.File
import java.security.MessageDigest

data class MediaReference(val id:String,val path:String,val size:Long,val sha256:String?=null)
data class RelinkCandidate(val reference:MediaReference,val file:File,val score:Int,val reason:String)
class MediaRelinker{fun missing(refs:List<MediaReference>)=refs.filter{!File(it.path).isFile};fun search(ref:MediaReference,roots:List<File>):List<RelinkCandidate>{val out=mutableListOf<RelinkCandidate>();for(root in roots.filter{it.exists()})root.walkTopDown().filter{it.isFile&&it.length()==ref.size}.forEach{val hash=ref.sha256;val exact=hash!=null&&sha(it)==hash;val name=File(ref.path).name.equals(it.name,true);if(exact||name)out+=RelinkCandidate(ref,it,if(exact)100 else 50,if(exact)"SHA-256 match" else "Filename and size match")};return out.sortedByDescending{it.score}};fun relink(ref:MediaReference,candidate:File)=ref.copy(path=candidate.absolutePath,size=candidate.length(),sha256=sha(candidate));private fun sha(f:File)=MessageDigest.getInstance("SHA-256").digest(f.inputStream().use{it.readBytes()}).joinToString(""){"%02x".format(it)}}

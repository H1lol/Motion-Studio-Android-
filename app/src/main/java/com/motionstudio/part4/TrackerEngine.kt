package com.motionstudio.part4
import kotlin.math.*
data class Point2(val x:Double,val y:Double)
data class TrackSample(val timeUs:Long,val position:Point2,val scale:Double,val rotationRad:Double,val confidence:Double)
class TrackerEngine{
 fun trackPoint(frames:List<FloatArray>,width:Int,height:Int,start:Point2,patch:Int=3,search:Int=8):List<TrackSample>{require(frames.isNotEmpty());var p=start;val out=mutableListOf<TrackSample>();for((fi,img)in frames.withIndex()){if(fi>0)p=find(img,frames[fi-1],width,height,p,patch,search);out+=TrackSample(fi.toLong(),p,1.0,0.0,1.0)};return out}
 private fun find(cur:FloatArray,prev:FloatArray,w:Int,h:Int,p:Point2,patch:Int,search:Int):Point2{var best=Double.POSITIVE_INFINITY;var bx=p.x.toInt();var by=p.y.toInt();for(dy in -search..search)for(dx in -search..search){val x=p.x.toInt()+dx;val y=p.y.toInt()+dy;if(x-patch<0||y-patch<0||x+patch>=w||y+patch>=h)continue;var e=0.0;for(j in -patch..patch)for(i in -patch..patch){val q=prev[((p.y.toInt()+j).coerceIn(0,h-1))*w+(p.x.toInt()+i).coerceIn(0,w-1)];val r=cur[(y+j)*w+x+i];e+=(q-r)*(q-r)};if(e<best){best=e;bx=x;by=y}};return Point2(bx.toDouble(),by.toDouble())}
}

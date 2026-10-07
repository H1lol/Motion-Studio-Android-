package com.motionstudio.part4
import kotlin.math.*
data class MotionVector(val dx:Float,val dy:Float,val confidence:Float)
object OpticalFlow{
 fun estimate(a:FloatArray,b:FloatArray,width:Int,height:Int,window:Int=2,search:Int=4):Array<MotionVector>{require(a.size==width*height&&b.size==a.size&&window>=1&&search>=1);val out=Array(width*height){MotionVector(0f,0f,0f)};for(y in window until height-window)for(x in window until width-window){var best=Float.POSITIVE_INFINITY;var bx=0;var by=0;for(dy in -search..search)for(dx in -search..search){var e=0f;for(j in -window..window)for(i in -window..window){val p=a[(y+j)*width+x+i];val xx=(x+i+dx).coerceIn(0,width-1);val yy=(y+j+dy).coerceIn(0,height-1);val q=b[yy*width+xx];e+=abs(p-q)};if(e<best){best=e;bx=dx;by=dy}};out[y*width+x]=MotionVector(bx.toFloat(),by.toFloat(),1f/(1f+best))};return out}
}

package com.motionstudio.part4
import com.motionstudio.part2.FrameBuffer
import kotlin.math.*
data class Scopes(val histogram:IntArray,val waveform:FloatArray,val vectorscope:FloatArray,val parade:FloatArray)
object ColorScopes{
 fun analyze(f:FrameBuffer,bins:Int=256):Scopes{require(bins>1);val h=IntArray(bins);val wave=FloatArray(f.width);val vec=FloatArray(bins*bins);val parade=FloatArray(f.width*3);for(y in 0 until f.height)for(x in 0 until f.width){val c=f.get(x,y);val lum=.2126f*c.r+.7152f*c.g+.0722f*c.b;h[(lum*(bins-1)).roundToInt().coerceIn(0,bins-1)]++;wave[x]=max(wave[x],lum);parade[x]=max(parade[x],c.r);parade[f.width+x]=max(parade[f.width+x],c.g);parade[2*f.width+x]=max(parade[2*f.width+x],c.b);val maxc=max(c.r,max(c.g,c.b));val minc=min(c.r,min(c.g,c.b));val d=maxc-minc;val hue=if(d<1e-6f)0f else when(maxc){c.r->((c.g-c.b)/d%6f)/6f;c.g->((c.b-c.r)/d+2f)/6f;else->((c.r-c.g)/d+4f)/6f};val sat=if(maxc<1e-6f)0f else d/maxc;val ix=(hue.mod(1f)*(bins-1)).roundToInt();val iy=((1-sat)*(bins-1)).roundToInt();vec[iy*bins+ix]+=1f};return Scopes(h,wave,vec,parade)}
}

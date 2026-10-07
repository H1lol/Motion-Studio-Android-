package com.motionstudio.part4
import com.motionstudio.part2.*
import kotlin.math.*
data class KeySettings(val key:Rgba=Rgba(0f,1f,0f,1f),val tolerance:Float=.2f,val softness:Float=.05f,val spill:Float=.5f,val lumaMin:Float=0f,val lumaMax:Float=1f)
object KeyerEngine{
 fun chroma(src:FrameBuffer,s:KeySettings):FrameBuffer{val o=FrameBuffer(src.width,src.height);for(y in 0 until src.height)for(x in 0 until src.width){val c=src.get(x,y);val d=sqrt((c.r-s.key.r).pow(2)+(c.g-s.key.g).pow(2)+(c.b-s.key.b).pow(2));val a=1-((s.tolerance-d)/max(s.softness,1e-5f)).coerceIn(0f,1f);val spill=max(0f,c.g-max(c.r,c.b))*s.spill;o.set(x,y,Rgba(c.r+spill,c.g-spill,c.b+spill,c.a*a))};return o}
 fun luma(src:FrameBuffer,s:KeySettings):FrameBuffer{val o=FrameBuffer(src.width,src.height);for(y in 0 until src.height)for(x in 0 until src.width){val c=src.get(x,y);val l=.2126f*c.r+.7152f*c.g+.0722f*c.b;val a=((l-s.lumaMin)/max(1e-5f,s.lumaMax-s.lumaMin)).coerceIn(0f,1f);o.set(x,y,Rgba(c.r,c.g,c.b,c.a*a))};return o}
 fun combine(a:FrameBuffer,b:FrameBuffer,mode:String="max"):FrameBuffer{require(a.width==b.width&&a.height==b.height);return a.mapPixels{it}.also{out->for(y in 0 until a.height)for(x in 0 until a.width){val p=a.get(x,y);val q=b.get(x,y);out.set(x,y,Rgba(p.r,p.g,p.b,if(mode=="multiply")p.a*q.a else if(mode=="min")min(p.a,q.a) else max(p.a,q.a)))}}}
}

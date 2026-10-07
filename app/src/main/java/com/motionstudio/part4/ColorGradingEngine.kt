package com.motionstudio.part4
import com.motionstudio.part2.*
import kotlin.math.*
data class Grade(val lift:Rgba=Rgba(0f,0f,0f,0f),val gamma:Rgba=Rgba(1f,1f,1f,1f),val gain:Rgba=Rgba(1f,1f,1f,1f),val offset:Rgba=Rgba(0f,0f,0f,0f),val exposure:Float=0f,val contrast:Float=1f,val saturation:Float=1f,val temperature:Float=0f,val tint:Float=0f,val shadows:Float=0f,val highlights:Float=0f)
object ColorGradingEngine{
 fun apply(src:FrameBuffer,g:Grade):FrameBuffer{require(g.contrast.isFinite()&&g.saturation.isFinite());val o=FrameBuffer(src.width,src.height);for(y in 0 until src.height)for(x in 0 until src.width){val c=src.get(x,y);val lum=.2126f*c.r+.7152f*c.g+.0722f*c.b;val sh=(1-lum).coerceIn(0f,1f);val hi=lum.coerceIn(0f,1f);var r=c.r*2f.pow(g.exposure)*g.gain.r+g.lift.r+g.offset.r;var gg=c.g*2f.pow(g.exposure)*g.gain.g+g.lift.g+g.offset.g;var b=c.b*2f.pow(g.exposure)*g.gain.b+g.lift.b+g.offset.b;r+=g.shadows*sh+g.highlights*hi;gg+=g.shadows*sh+g.highlights*hi;b+=g.shadows*sh+g.highlights*hi;r=(r-.5f)*g.contrast+.5f;gg=(gg-.5f)*g.contrast+.5f;b=(b-.5f)*g.contrast+.5f;r+=g.temperature*.1f;b-=g.temperature*.1f;gg+=g.tint*.05f;val q=.2126f*r+.7152f*gg+.0722f*b;r=q+(r-q)*g.saturation;gg=q+(gg-q)*g.saturation;b=q+(b-q)*g.saturation;o.set(x,y,Rgba(r.coerceIn(0f,1f),gg.coerceIn(0f,1f),b.coerceIn(0f,1f),c.a))};return o}
}

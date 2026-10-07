package com.motionstudio.part4
import kotlin.math.*
data class StereoSample(val left:Float,val right:Float)
data class AudioClip(val samples:FloatArray,val sampleRate:Int,val channels:Int=2,val startUs:Long=0,val gain:Float=1f,val pan:Float=0f,val mute:Boolean=false,val solo:Boolean=false)
class AudioEngine{
 fun mix(clips:List<AudioClip>,sampleCount:Int):FloatArray{require(sampleCount>=0);if(clips.isEmpty())return FloatArray(sampleCount*2);val active=if(clips.any{it.solo})clips.filter{it.solo&&!it.mute}else clips.filterNot{it.mute};val out=FloatArray(sampleCount*2);for(c in active){require(c.sampleRate>0&&c.channels in 1..2&&c.gain.isFinite());val pan=c.pan.coerceIn(-1f,1f);val lGain=c.gain*(1-pan).coerceAtLeast(0f);val rGain=c.gain*(1+pan).coerceAtLeast(0f);for(i in 0 until sampleCount){val s=if(c.channels==1)c.samples.getOrElse(i){0f}else c.samples.getOrElse(i*2){0f};val sr=if(c.channels==1)s else c.samples.getOrElse(i*2+1){0f};out[i*2]=(out[i*2]+s*lGain).coerceIn(-1f,1f);out[i*2+1]=(out[i*2+1]+sr*rGain).coerceIn(-1f,1f)}};return out}
 fun applyGainPan(interleaved:FloatArray,gain:Float,pan:Float):FloatArray{require(gain.isFinite());val o=interleaved.copyOf();val l=gain*(1-pan.coerceIn(-1f,1f));val r=gain*(1+pan.coerceIn(-1f,1f));var i=0;while(i+1<o.size){o[i]=(o[i]*l).coerceIn(-1f,1f);o[i+1]=(o[i+1]*r).coerceIn(-1f,1f);i+=2};return o}
}

package com.motionstudio.part4
import kotlin.math.*
data class WaveformLevel(val bucketSize:Int,val rms:FloatArray,val peaks:FloatArray,val stereoPeaks:FloatArray)
object AudioWaveform{
 fun generate(samples:FloatArray,channels:Int,bucketSize:Int):WaveformLevel{require(channels in 1..2&&bucketSize>0);val n=ceil(samples.size.toDouble()/channels/bucketSize).toInt();val rms=FloatArray(n);val peaks=FloatArray(n);val stereo=FloatArray(n*2);for(b in 0 until n){var sum=0.0;var count=0;var pl=0f;var pr=0f;for(i in b*bucketSize until min((b+1)*bucketSize,samples.size/channels)){val l=samples[i*channels];val r=if(channels==2)samples[i*2+1] else l;sum+=l*l+r*r;count+=2;pl=max(pl,abs(l));pr=max(pr,abs(r))};rms[b]=sqrt(if(count==0)0.0 else sum/count).toFloat();peaks[b]=max(pl,pr);stereo[b*2]=pl;stereo[b*2+1]=pr};return WaveformLevel(bucketSize,rms,peaks,stereo)}
 fun zoom(level:WaveformLevel,from:Int,to:Int):WaveformLevel{val a=from.coerceIn(0,level.rms.size);val b=to.coerceIn(a,level.rms.size);return WaveformLevel(level.bucketSize,level.rms.copyOfRange(a,b),level.peaks.copyOfRange(a,b),level.stereoPeaks.copyOfRange(a*2,b*2))}
}

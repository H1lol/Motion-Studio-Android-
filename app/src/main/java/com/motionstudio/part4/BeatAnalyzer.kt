package com.motionstudio.part4
import kotlin.math.*
data class BeatAnalysis(val tempoBpm:Double,val beats:List<Long>,val onsets:List<Long>)
object BeatAnalyzer{
 fun analyze(samples:FloatArray,sampleRate:Int):BeatAnalysis{require(sampleRate>0);val mono=FloatArray(samples.size/2){(samples[it*2]+samples[it*2+1])*0.5f};val hop=512;val flux=DoubleArray(max(0,mono.size/hop-1));var prev=0.0;for(k in flux.indices){var e=0.0;for(i in k*hop until min((k+1)*hop,mono.size))e+=mono[i]*mono[i];val f=max(0.0,e-prev);flux[k]=f;prev=e};val threshold=flux.averageOrZero()*1.5;val onsets=flux.indices.filter{flux[it]>threshold&&flux[it]>=flux.getOrElse(it-1){-1.0}&&flux[it]>=flux.getOrElse(it+1){-1.0}}.map{(it+1)*hop*1_000_000L/sampleRate};val intervals=onsets.zipWithNext{a,b->b-a}.filter{it>100_000};val bpm=if(intervals.isEmpty())120.0 else (60_000_000.0/intervals.median()).coerceIn(40.0,240.0);val period=(60_000_000.0/bpm).toLong();val first=onsets.firstOrNull()?:0L;val beats=if(onsets.isEmpty())emptyList() else generateSequence(first){it+period}.takeWhile{it<=mono.size*1_000_000L/sampleRate}.toList();return BeatAnalysis(bpm,beats,onsets)}
 private fun DoubleArray.averageOrZero()=if(isEmpty())0.0 else average()
 private fun List<Long>.median():Double{val s=sorted();return if(s.size%2==1)s[s.size/2].toDouble() else (s[s.size/2-1]+s[s.size/2])/2.0}
}

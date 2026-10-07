package com.motionstudio.part4
import kotlin.math.*
data class SpeedKey(val timeUs:Long,val speed:Double)
class TimeRemapEngine(private val keys:List<SpeedKey>){init{require(keys.all{it.timeUs>=0&&it.speed.isFinite()&&it.speed>=0});require(keys.zipWithNext().all{it.first.timeUs<it.second.timeUs})}
 fun sourceTimeUs(outputUs:Long):Long{require(outputUs>=0);if(keys.isEmpty())return outputUs;var acc=0.0;var last=0L;for(k in keys){if(outputUs<=k.timeUs)return (last+(outputUs-last)*speedAt((last+k.timeUs)/2)).toLong();acc+=(k.timeUs-last)*speedAt((last+k.timeUs)/2);if(acc>=outputUs)return (last+(outputUs-(acc-(k.timeUs-last)))/max(1e-9,speedAt((last+k.timeUs)/2))).toLong();last=k.timeUs};return (last+(outputUs-acc)/max(1e-9,speedAt(last))).toLong()}
 fun speedAt(t:Long):Double{if(keys.isEmpty())return 1.0;if(t<=keys.first().timeUs)return keys.first().speed;if(t>=keys.last().timeUs)return keys.last().speed;val b=keys.zipWithNext().first{t in it.first.timeUs..it.second.timeUs};val u=(t-b.first.timeUs).toDouble()/(b.second.timeUs-b.first.timeUs);return b.first.speed+(b.second.speed-b.first.speed)*u}
 fun freezeAt(timeUs:Long)=TimeRemapEngine(listOf(SpeedKey(0,1.0),SpeedKey(timeUs,0.0),SpeedKey(timeUs+1,0.0),SpeedKey(timeUs+2,1.0)))
}

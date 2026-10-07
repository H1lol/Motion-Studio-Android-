package com.motionstudio.app

import kotlin.math.max
import kotlin.math.min

enum class Interpolation { HOLD, LINEAR, BEZIER }
data class EditableKeyframe(val id:String,var timeUs:Long,var value:Double,var inTangent:Double=0.0,var outTangent:Double=0.0,var interpolation:Interpolation=Interpolation.BEZIER)
class KeyframeEditor(private val keys:MutableList<EditableKeyframe>) {
    fun sorted():List<EditableKeyframe> = keys.sortedBy { it.timeUs }
    fun add(timeUs:Long,value:Double):EditableKeyframe { val k=EditableKeyframe(java.util.UUID.randomUUID().toString(),timeUs,value);keys+=k;return k }
    fun move(id:String,timeUs:Long,value:Double) { keys.firstOrNull{it.id==id}?.apply{this.timeUs=max(0L,timeUs);this.value=value} }
    fun remove(id:String){keys.removeAll{it.id==id}}
    fun setInterpolation(id:String,i:Interpolation){keys.firstOrNull{it.id==id}?.interpolation=i}
    fun evaluate(timeUs:Long):Double { val s=sorted(); if(s.isEmpty()) return 0.0; if(timeUs<=s.first().timeUs)return s.first().value;if(timeUs>=s.last().timeUs)return s.last().value; val b=s.indexOfLast{it.timeUs<=timeUs};val a=s[b];val c=s[b+1];if(a.interpolation==Interpolation.HOLD)return a.value;val u=(timeUs-a.timeUs).toDouble()/(c.timeUs-a.timeUs).toDouble();return if(a.interpolation==Interpolation.LINEAR)a.value+(c.value-a.value)*u else { val u2=u*u;val u3=u2*u; val p0=a.value;val p1=a.value+a.outTangent;val p2=c.value-c.inTangent;val p3=c.value; (2*u3-3*u2+1)*p0+(u3-2*u2+u)*p1+(-2*u3+3*u2)*p2+(u3-u2)*p3 } }
    fun valueRange():Pair<Double,Double>{if(keys.isEmpty())return 0.0 to 1.0;val v=keys.map{it.value};return v.minOrNull()!! to v.maxOrNull()!!}
}

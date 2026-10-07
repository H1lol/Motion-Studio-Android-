package com.motionstudio.app

import com.motionstudio.part1.*

data class TimelineViewport(var pixelsPerSecond: Float = 100f, var scrollUs: Long = 0L)
data class TimelineHit(val layerId: String, val localUs: Long)

class TimelineController(private val composition: Composition) {
    val engine = TimelineEngine(composition)
    var viewport = TimelineViewport()
    fun xToTimeUs(x: Float): Long = (viewport.scrollUs + x.coerceAtLeast(0f) / viewport.pixelsPerSecond * 1_000_000f).toLong().coerceIn(0,composition.durationUs)
    fun timeToX(timeUs: Long): Float = (timeUs-viewport.scrollUs).coerceAtLeast(0L)/1_000_000f*viewport.pixelsPerSecond
    fun hitTest(x: Float, y: Float, rowHeight: Float = 56f): TimelineHit? { val i=(y/rowHeight).toInt(); val layer=composition.layers.asReversed().getOrNull(i) ?: return null; val t=xToTimeUs(x); return if(t in layer.startUs..(layer.startUs+layer.durationUs)) TimelineHit(layer.id,t-layer.startUs) else null }
    fun trim(id:String, newStartUs:Long, newDurationUs:Long) { composition.layers.firstOrNull{it.id==id}?.let { if(!it.locked){it.startUs=newStartUs.coerceAtLeast(0);it.durationUs=newDurationUs.coerceAtLeast(1)}} }
    fun move(id:String, deltaUs:Long) { composition.layers.firstOrNull{it.id==id}?.let { if(!it.locked) it.startUs=(it.startUs+deltaUs).coerceAtLeast(0) } }
    fun duplicate(id:String):Layer? { val l=composition.layers.firstOrNull{it.id==id} ?: return null; val copy=l.copy(id=java.util.UUID.randomUUID().toString(),name="${l.name} Copy",startUs=l.startUs+100_000); composition.layers.add(copy); return copy }
    fun reorder(id:String, targetIndex:Int) { val l=composition.layers.firstOrNull{it.id==id} ?: return; composition.layers.remove(l); composition.layers.add(targetIndex.coerceIn(0,composition.layers.size),l) }
}

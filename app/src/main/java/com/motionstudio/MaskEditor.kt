package com.motionstudio.app

import com.motionstudio.part1.*

data class EditablePathPoint(val id:String,var position:Vec2,var inHandle:Vec2=Vec2(0f,0f),var outHandle:Vec2=Vec2(0f,0f),var selected:Boolean=false)
class MaskEditor(private val points:MutableList<EditablePathPoint> = mutableListOf()) {
    fun points():List<EditablePathPoint> = points.toList()
    fun add(p:Vec2)=EditablePathPoint(java.util.UUID.randomUUID().toString(),p).also(points::add)
    fun remove(id:String){points.removeAll{it.id==id}}
    fun move(id:String,p:Vec2){points.firstOrNull{it.id==id}?.position=p}
    fun setHandles(id:String,inHandle:Vec2,outHandle:Vec2){points.firstOrNull{it.id==id}?.apply{this.inHandle=inHandle;this.outHandle=outHandle}}
    fun select(id:String){points.forEach{it.selected=it.id==id}}
    fun toPath(closed:Boolean=true):MaskPath=MaskPath(points.map{it.position}.toMutableList(),closed)
}

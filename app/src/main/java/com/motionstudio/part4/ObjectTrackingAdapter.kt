package com.motionstudio.part4
data class BoundingBox(val left:Float,val top:Float,val right:Float,val bottom:Float){val width get()=right-left;val height get()=bottom-top}
data class Detection(val label:String,val score:Float,val box:BoundingBox)
interface ObjectTrackingAdapter{fun available():Boolean;fun detect(rgba:ByteArray,width:Int,height:Int):List<Detection>;fun track(previous:List<Detection>,rgba:ByteArray,width:Int,height:Int):List<Detection>}
class UnavailableObjectTrackingAdapter:ObjectTrackingAdapter{override fun available()=false;override fun detect(rgba:ByteArray,width:Int,height:Int)=emptyList<Detection>();override fun track(previous:List<Detection>,rgba:ByteArray,width:Int,height:Int)=emptyList<Detection>()}

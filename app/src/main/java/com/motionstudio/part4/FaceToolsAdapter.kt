package com.motionstudio.part4
data class FaceBox(val box:BoundingBox,val confidence:Float)
data class FaceLandmarks(val points:List<Point2>,val confidence:Float)
interface FaceToolsAdapter{fun available():Boolean;fun detectFaces(rgba:ByteArray,width:Int,height:Int):List<FaceBox>;fun landmarks(rgba:ByteArray,width:Int,height:Int,face:FaceBox):FaceLandmarks?}
class UnavailableFaceToolsAdapter:FaceToolsAdapter{override fun available()=false;override fun detectFaces(rgba:ByteArray,width:Int,height:Int)=emptyList<FaceBox>();override fun landmarks(rgba:ByteArray,width:Int,height:Int,face:FaceBox)=null}

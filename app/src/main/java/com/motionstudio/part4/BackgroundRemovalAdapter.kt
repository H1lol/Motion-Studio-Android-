package com.motionstudio.part4
data class SegmentationMask(val width:Int,val height:Int,val alpha:FloatArray)
interface BackgroundRemovalAdapter{fun available():Boolean;fun segment(rgba:ByteArray,width:Int,height:Int):SegmentationMask?}
class UnavailableBackgroundRemovalAdapter:BackgroundRemovalAdapter{override fun available()=false;override fun segment(rgba:ByteArray,width:Int,height:Int)=null}
class AndroidSegmentationAdapter(private val provider:(ByteArray,Int,Int)->SegmentationMask?):BackgroundRemovalAdapter{override fun available()=true;override fun segment(rgba:ByteArray,width:Int,height:Int)=provider(rgba,width,height)}
object MaskRefiner{fun temporal(previous:SegmentationMask,current:SegmentationMask,amount:Float=.7f):SegmentationMask{require(previous.width==current.width&&previous.height==current.height);val a=amount.coerceIn(0f,1f);return SegmentationMask(current.width,current.height,FloatArray(current.alpha.size){previous.alpha[it]*(a)+current.alpha[it]*(1-a)})}}

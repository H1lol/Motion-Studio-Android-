package com.motionstudio.part4
import java.io.Closeable
data class MediaInfo(val uri:String,val durationUs:Long,val frameRate:Double?,val width:Int,val height:Int,val rotation:Int,val videoCodec:String?,val audioCodec:String?,val variableFrameRate:Boolean,val hasAlpha:Boolean)
data class VideoFrame(val ptsUs:Long,val width:Int,val height:Int,val rgba:ByteArray)
interface FrameDecoder:Closeable{val info:MediaInfo;fun seekTo(timeUs:Long):Boolean;fun decodeNext():VideoFrame?}
class MediaEngine(private val factory:(String)->FrameDecoder?){
 private var decoder:FrameDecoder?=null
 @Synchronized fun open(uri:String):MediaInfo{require(uri.isNotBlank());decoder?.close();decoder=factory(uri)?:throw UnsupportedMediaException("No decoder available: $uri");return decoder!!.info}
 @Synchronized fun seek(timeUs:Long)=decoder?.seekTo(timeUs.coerceAtLeast(0))?:false
 @Synchronized fun readFrame()=decoder?.decodeNext()
 @Synchronized fun close(){decoder?.close();decoder=null}
}
class UnsupportedMediaException(message:String):Exception(message)

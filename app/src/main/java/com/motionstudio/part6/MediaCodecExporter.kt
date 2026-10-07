package com.motionstudio.part6
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

data class EncoderConfig(val mime:String,val width:Int,val height:Int,val bitrate:Int,val fps:Int,val iFrameInterval:Int=2,val colorFormat:Int?=null)
class MediaCodecExporter(private val codecFactory:(String)->MediaCodec={ mime -> MediaCodec.createEncoderByType(mime) }){private var codec:MediaCodec?=null;private var started=false
 fun configure(c:EncoderConfig){close();val x=codecFactory(c.mime);val f=MediaFormat.createVideoFormat(c.mime,c.width,c.height);f.setInteger(MediaFormat.KEY_BIT_RATE,c.bitrate);f.setInteger(MediaFormat.KEY_FRAME_RATE,c.fps);f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,c.iFrameInterval);c.colorFormat?.let{f.setInteger(MediaFormat.KEY_COLOR_FORMAT,it)};x.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec=x}
 fun start(){val x=codec?:error("Encoder not configured");x.start();started=true}
 fun codecInfo():MediaCodecInfo?=codec?.codecInfo
 fun dequeueInput(timeoutUs:Long)=codec?.dequeueInputBuffer(timeoutUs)?:-1
 fun inputBuffer(index:Int):ByteBuffer=codec?.getInputBuffer(index)?:error("No input buffer")
 fun queueInput(index:Int,size:Int,presentationTimeUs:Long,flags:Int=0){codec?.queueInputBuffer(index,0,size,presentationTimeUs,flags)}
 fun dequeueOutput(info:MediaCodec.BufferInfo,timeoutUs:Long)=codec?.dequeueOutputBuffer(info,timeoutUs)?:MediaCodec.INFO_TRY_AGAIN_LATER
 fun outputBuffer(index:Int):ByteBuffer=codec?.getOutputBuffer(index)?:error("No output buffer")
 fun releaseOutput(index:Int,render:Boolean=false){codec?.releaseOutputBuffer(index,render)}
 fun signalEnd(){val i=dequeueInput(10_000);if(i>=0)queueInput(i,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)}
 fun flush(){codec?.flush()};fun close(){try{if(started)codec?.stop()}finally{codec?.release();codec=null;started=false}}
}

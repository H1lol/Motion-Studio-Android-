package com.motionstudio.part4
import android.media.*
import android.view.Surface
import android.os.Bundle
class MediaCodecAdapter(private val codec:MediaCodec):AutoCloseable{
 private var started=false
 fun configure(format:MediaFormat,surface:Surface?,flags:Int=0){check(!started);codec.configure(format,surface,null,flags)}
 fun start(){if(!started){codec.start();started=true}}
 fun dequeueInput(timeoutUs:Long=10_000)=codec.dequeueInputBuffer(timeoutUs)
 fun inputBuffer(index:Int)=codec.getInputBuffer(index)
 fun queueInput(index:Int,size:Int,ptsUs:Long,flags:Int=0){require(index>=0&&size>=0&&ptsUs>=0);codec.queueInputBuffer(index,0,size,ptsUs,flags)}
 fun dequeueOutput(info:MediaCodec.BufferInfo,timeoutUs:Long=10_000)=codec.dequeueOutputBuffer(info,timeoutUs)
 fun outputBuffer(index:Int)=if(index>=0)codec.getOutputBuffer(index)else null
 fun releaseOutput(index:Int,render:Boolean=false){if(index>=0)codec.releaseOutputBuffer(index,render)}
 fun flush(){if(started)codec.flush()}
 fun setParameters(bundle:Bundle){if(started)codec.setParameters(bundle)}
 override fun close(){if(started)runCatching{codec.stop()};codec.release();started=false}
 companion object{fun createDecoder(mime:String)=MediaCodec.createDecoderByType(mime)}
}

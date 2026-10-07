package com.motionstudio.part6
import android.media.MediaMuxer
import android.media.MediaFormat
import android.media.MediaCodec
import java.io.File
import java.nio.ByteBuffer
class MediaMuxerExporter(private val output:File,private val format:Int=MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4){private var muxer:MediaMuxer?=null;private var video=-1;private var audio=-1;private var started=false
 fun addVideoTrack(f:MediaFormat):Int{check(!started);video=muxer().addTrack(f);return video};fun addAudioTrack(f:MediaFormat):Int{check(!started);audio=muxer().addTrack(f);return audio}
 private fun muxer():MediaMuxer{if(muxer==null){output.parentFile?.mkdirs();muxer=MediaMuxer(output.absolutePath,format)};return muxer!!}
 fun start(){check(video>=0||audio>=0);muxer().start();started=true}
 @Synchronized fun writeVideo(buf:ByteBuffer,info:MediaCodec.BufferInfo){write(video,buf,info)};@Synchronized fun writeAudio(buf:ByteBuffer,info:MediaCodec.BufferInfo){write(audio,buf,info)}
 private fun write(track:Int,buf:ByteBuffer,info:MediaCodec.BufferInfo){check(started);require(track>=0);require(info.presentationTimeUs>=0);val dup=buf.duplicate();dup.position(info.offset.coerceAtLeast(0));dup.limit((info.offset+info.size).coerceAtMost(buf.capacity()));muxer!!.writeSampleData(track,dup,info)}
 fun stop(){if(started){muxer?.stop();started=false}};fun close(){try{stop()}finally{muxer?.release();muxer=null;video=-1;audio=-1}}
}

package com.motionstudio.part6
import com.motionstudio.part2.FrameBuffer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class ExportFrame(val index:Long,val presentationTimeUs:Long,val frame:FrameBuffer)
data class ExportRequest(val profile:ExportProfile,val output:File,val durationUs:Long,val frameProvider:(Long)->FrameBuffer,val audioProvider:(Long)->ByteArray?= {null})
interface ExportSink{fun writeVideo(frame:ExportFrame);fun writeAudio(presentationTimeUs:Long,data:ByteArray);fun finish();fun close()}
class ExportEngine(private val capabilities:CodecCapabilityProvider){interface CodecCapabilityProvider{fun videoCapabilities():List<CodecCapability>;fun audioCapabilities():List<AudioCodecCapability>}
 fun validate(p:ExportProfile)=ExportProfiles.validate(p,capabilities.videoCapabilities(),capabilities.audioCapabilities())
 fun export(r:ExportRequest, sink:ExportSink, cancelled:()->Boolean={false},progress:(Double)->Unit={} ){validate(r.profile);require(r.durationUs>=0);val frameStep=1_000_000.0/r.profile.fps;var t=0L;var i=0L;try{while(t<=r.durationUs){if(cancelled())throw java.util.concurrent.CancellationException();val fb=r.frameProvider(t);sink.writeVideo(ExportFrame(i,t,fb));r.audioProvider(t)?.let{sink.writeAudio(t,it)};progress((t.toDouble()/r.durationUs.coerceAtLeast(1)).coerceIn(0.0,1.0));i++;t=(i*frameStep).toLong()};sink.finish()}finally{sink.close()}}
}

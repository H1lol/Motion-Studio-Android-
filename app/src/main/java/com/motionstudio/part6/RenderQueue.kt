package com.motionstudio.part6
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

enum class JobState{QUEUED,RUNNING,PAUSED,CANCELLED,FAILED,COMPLETED}
data class RenderJob(val id:String=UUID.randomUUID().toString(),val profile:ExportProfile,val output:File,val createdAt:Long=System.currentTimeMillis(),var attempts:Int=0,var progress:Double=0.0,var state:JobState=JobState.QUEUED,var error:String?=null)
fun interface RenderJobRunner{fun run(job:RenderJob,control:RenderQueue.Control,progress:(Double)->Unit)}
class RenderQueue(private val stateFile:File?=null,private val runner:RenderJobRunner){
 interface Control{fun isCancelled():Boolean;fun isPaused():Boolean;fun awaitIfPaused()}
 private val jobs=CopyOnWriteArrayList<RenderJob>();private val cancelled=ConcurrentHashMap.newKeySet<String>();private val paused=java.util.concurrent.atomic.AtomicBoolean(false);private val worker=AtomicReference<Thread?>(null)
 init{load()}
 @Synchronized fun enqueue(j:RenderJob){require(j.state==JobState.QUEUED);jobs.add(j);persist();start()}
 fun snapshot()=jobs.toList()
 fun pause(){paused.set(true);jobs.find{it.state==JobState.RUNNING}?.state=JobState.PAUSED;persist()}
 fun resume(){paused.set(false);jobs.find{it.state==JobState.PAUSED}?.state=JobState.QUEUED;persist();start()}
 fun cancel(id:String){jobs.find{it.id==id}?.let{it.state=JobState.CANCELLED;cancelled+=id;persist()}}
 fun retry(id:String){jobs.find{it.id==id&&it.state in setOf(JobState.FAILED,JobState.CANCELLED)}?.let{it.attempts++;it.error=null;it.progress=0.0;it.state=JobState.QUEUED;cancelled.remove(id);persist();start()}}
 private fun start(){if(worker.get()?.isAlive==true)return;worker.set(thread(start=true,name="MotionStudio-RenderQueue"){loop()})}
 private fun loop(){while(true){val j=jobs.firstOrNull{it.state==JobState.QUEUED}?:break;j.state=JobState.RUNNING;persist();val c=object:Control{override fun isCancelled()=j.id in cancelled||j.state==JobState.CANCELLED;override fun isPaused()=paused.get();override fun awaitIfPaused(){while(paused.get()&&!isCancelled())Thread.sleep(25)}};try{runner.run(j,c){j.progress=it.coerceIn(0.0,1.0);persist()};if(c.isCancelled())j.state=JobState.CANCELLED else{j.progress=1.0;j.state=JobState.COMPLETED}}catch(t:Throwable){if(c.isCancelled())j.state=JobState.CANCELLED else{j.state=JobState.FAILED;j.error=t.message?:t::class.java.name}}finally{persist()}}}
 private fun persist(){val f=stateFile?:return;f.parentFile?.mkdirs();val tmp=File(f.parentFile,f.name+".tmp");tmp.writeText(jobs.joinToString("\n"){serialize(it)});require(tmp.renameTo(f))}
 private fun serialize(j:RenderJob)=listOf(j.id,j.createdAt,j.attempts,j.progress,j.state,j.output.absolutePath,j.error.orEmpty(),j.profile.id,j.profile.name,j.profile.width,j.profile.height,j.profile.fps,j.profile.videoCodec,j.profile.bitrate,j.profile.pixelFormat,j.profile.audioCodec,j.profile.audioBitrate?:-1,j.profile.container,j.profile.quality,j.profile.alpha).joinToString("\t"){escape(it.toString())}
 private fun load(){val f=stateFile?:return;if(!f.isFile)return;f.readLines().forEach{val p=it.split('\t').map(::unescape);if(p.size>=20)runCatching{jobs+=RenderJob(p[0],ExportProfile(p[7],p[8],p[9].toInt(),p[10].toInt(),p[11].toDouble(),VideoCodec.valueOf(p[12]),p[13].toInt(),PixelFormat.valueOf(p[14]),p[15].takeIf{it!="null"}?.let(AudioCodec::valueOf),p[16].toInt().takeIf{it>=0},Container.valueOf(p[17]),Quality.valueOf(p[18]),p[19].toBoolean()),File(p[5]),p[1].toLong(),p[2].toInt(),p[3].toDouble(),JobState.valueOf(p[4]),p[6].ifBlank{null})}.getOrNull()}}
 private fun escape(s:String)=s.replace("%","%25").replace("\t","%09").replace("\n","%0A");private fun unescape(s:String)=s.replace("%0A","\n").replace("%09","\t").replace("%25","%")
}

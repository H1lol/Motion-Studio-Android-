package com.motionstudio.part6
import com.motionstudio.part2.FrameBuffer
import kotlin.system.measureNanoTime

data class BenchmarkResult(val name:String,val iterations:Int,val totalMs:Double,val averageMs:Double,val throughput:Double)
class BenchmarkEngine{fun run(name:String,iterations:Int,operation:()->Unit):BenchmarkResult{require(iterations>0);repeat(2){operation()};val ns=measureNanoTime{repeat(iterations){operation()}};val ms=ns/1e6;return BenchmarkResult(name,iterations,ms,ms/iterations,iterations/(ms/1000.0).coerceAtLeast(1e-9))};fun cpu(iterations:Int,work:()->Unit)=run("cpu",iterations,work);fun gpu(iterations:Int,work:()->Unit)=run("gpu",iterations,work);fun decode(iterations:Int,work:()->Unit)=run("decode",iterations,work);fun encode(iterations:Int,work:()->Unit)=run("encode",iterations,work);fun memory(iterations:Int,allocator:()->ByteArray)=run("memory",iterations){allocator()};fun effects(iterations:Int,work:()->Unit)=run("effects",iterations,work);fun playback(iterations:Int,work:()->Unit)=run("timeline-playback",iterations,work)}

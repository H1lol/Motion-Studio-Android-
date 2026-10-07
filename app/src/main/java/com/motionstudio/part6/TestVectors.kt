package com.motionstudio.part6
import com.motionstudio.part2.*
import com.motionstudio.part3.*
import com.motionstudio.part5.*
import java.io.File
import kotlin.math.abs

data class VectorResult(val name:String,val passed:Boolean,val detail:String)
object TestVectors{
 fun runAll():List<VectorResult>{val r=mutableListOf<VectorResult>();r+=test("transform"){val v=Vector3(1.0,2.0,3.0);v+Vector3(2.0,3.0,4.0)==Vector3(3.0,5.0,7.0)};r+=test("blending"){BlendModes.blend(Rgba.WHITE,Rgba(0f,0f,0f,1f),BlendMode.NORMAL).a==1f};r+=test("masks"){val a=FrameBuffer(1,1);a.set(0,0,Rgba.WHITE);a.get(0,0).a==1f};r+=test("keyframes"){val k=KeyframeMath.scalar();k.add(Keyframe(0.0,0.0));k.add(Keyframe(1.0,10.0));abs((k.evaluate(.5)?:0.0)-5)<1e-9};r+=test("color"){Rgba(1f,.5f,0f,1f).r==1f};r+=test("compositing"){BlendModes.blend(Rgba(1f,0f,0f,.5f),Rgba(0f,0f,1f,1f),BlendMode.NORMAL).a>0.99f};r+=test("serialization"){val p=Project("p",mutableListOf(Composition("c",1920,1080,30.0,2.0)));val s=ProjectSerializer();val a=s.serialize(p);val b=s.serialize(p);a.contentEquals(b)};r+=test("plugin-metadata"){val d=AssetDescriptor("x","x","x.ffx","ffx",AssetCategory.PRESET,"1","test","h",1,emptySet(),Compatibility("Android","ARM64",true,"ok"),ExecutionMode.PORTABLE);d.extension=="ffx"};r+=test("dependency-resolution"){DependencyResolver(mapOf("a" to DependencyRef("a","effect"))).resolve(listOf(DependencyRef("a","effect")),emptyMap()).missing.isEmpty()};r+=test("export-timestamps"){ExportTimestampValidator.validate(listOf(0L,33333L,66666L),30.0)};return r}
 private fun test(n:String,f:()->Boolean)=runCatching{if(f())VectorResult(n,true,"pass")else VectorResult(n,false,"assertion failed")}.getOrElse{VectorResult(n,false,it.message?:"failure")}
}
object ExportTimestampValidator{fun validate(ts:List<Long>,fps:Double):Boolean{if(ts.isEmpty()||fps<=0)return false;return ts.zipWithNext().all{it.second>it.first&&abs((it.second-it.first)-1_000_000.0/fps)<2_000.0}}}

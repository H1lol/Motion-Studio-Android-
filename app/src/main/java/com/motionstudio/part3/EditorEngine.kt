package com.motionstudio.part3
import com.motionstudio.part2.*
data class Composition(val id:String,val width:Int,val height:Int,val fps:Double,val duration:Double){init{require(width>0&&height>0&&fps>0&&duration>=0)}}
data class Project(val id:String,val compositions:MutableList<Composition> = mutableListOf())
class EditorEngine(val project:Project){var composition:Composition?=null;var playhead=0.0;var playing=false;private set;fun open(c:Composition){require(c in project.compositions);composition=c;playhead=0.0};fun seek(t:Double){val c=composition?:return;playhead=t.coerceIn(0.0,c.duration)};fun step(frames:Int=1){val c=composition?:return;seek(playhead+frames/c.fps)};fun play(){playing=true};fun pause(){playing=false};fun renderFrame(renderer:(Composition,Double)->FrameBuffer):FrameBuffer{val c=composition?:error("No composition open");return renderer(c,playhead)}}

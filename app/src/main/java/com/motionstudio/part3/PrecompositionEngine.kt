package com.motionstudio.part3
import com.motionstudio.part2.*
class PrecompositionEngine(private val renderer:(Composition,Double)->FrameBuffer){fun render(comp:Composition,time:Double):FrameBuffer{require(time>=0&&time<=comp.duration);return renderer(comp,time)};fun renderNested(parent:Composition,child:Composition,time:Double,offset:Double=0.0,timeScale:Double=1.0):FrameBuffer{require(timeScale.isFinite()&&timeScale!=0.0);val local=((time-offset)*timeScale).coerceIn(0.0,child.duration);return renderer(child,local)}}

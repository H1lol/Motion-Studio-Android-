package com.motionstudio.part4
import kotlin.math.*
data class Transform2D(val tx:Double,val ty:Double,val rotation:Double,val scale:Double=1.0)
data class StabilizationResult(val transforms:List<Transform2D>,val cropScale:Double)
object Stabilizer{
 fun stabilize(motions:List<Transform2D>,strength:Double=1.0):StabilizationResult{require(strength in 0.0..1.0);if(motions.isEmpty())return StabilizationResult(emptyList(),1.0);val win=5;val sm=motions.indices.map{idx->var sx=0.0;var sy=0.0;var sr=0.0;var ss=0.0;var n=0;for(i in max(0,idx-win)..min(motions.lastIndex,idx+win)){sx+=motions[i].tx;sy+=motions[i].ty;sr+=motions[i].rotation;ss+=motions[i].scale;n++};Transform2D(sx/n,sy/n,sr/n,ss/n)};val out=motions.mapIndexed{i,m->Transform2D((sm[i].tx-m.tx)*strength,(sm[i].ty-m.ty)*strength,(sm[i].rotation-m.rotation)*strength,1+(sm[i].scale-m.scale)*strength)};val maxShift=out.maxOf{hypot(it.tx,it.ty)};return StabilizationResult(out,1.0+maxShift.coerceAtLeast(0.0)/100.0)}
}

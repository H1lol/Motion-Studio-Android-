package com.motionstudio.part4
import kotlin.math.*
data class Homography(val m:DoubleArray){init{require(m.size==9&&m.all{it.isFinite()})}}
object PlanarTracker{
 fun estimateHomography(src:List<Point2>,dst:List<Point2>):Homography{require(src.size==dst.size&&src.size>=4);val a=Array(8){DoubleArray(8)};val b=DoubleArray(8);for(i in 0 until 4){val x=src[i].x;val y=src[i].y;val u=dst[i].x;val v=dst[i].y;val r=i*2;a[r][0]=x;a[r][1]=y;a[r][2]=1.0;a[r][6]=-u*x;a[r][7]=-u*y;b[r]=u;a[r+1][3]=x;a[r+1][4]=y;a[r+1][5]=1.0;a[r+1][6]=-v*x;a[r+1][7]=-v*y;b[r+1]=v};val h=solve(a,b);return Homography(doubleArrayOf(h[0],h[1],h[2],h[3],h[4],h[5],h[6],h[7],1.0))}
 private fun solve(a0:Array<DoubleArray>,b0:DoubleArray):DoubleArray{val a=Array(8){a0[it].clone()};val b=b0.clone();for(c in 0 until 8){var p=c;for(r in c+1 until 8)if(abs(a[r][c])>abs(a[p][c]))p=r;require(abs(a[p][c])>1e-12);val tr=a[p];a[p]=a[c];a[c]=tr;val tb=b[p];b[p]=b[c];b[c]=tb;val d=a[c][c];for(j in c until 8)a[c][j]/=d;b[c]/=d;for(r in 0 until 8)if(r!=c){val f=a[r][c];for(j in c until 8)a[r][j]-=f*a[c][j];b[r]-=f*b[c]}};return b}
 fun map(h:Homography,p:Point2):Point2{val m=h.m;val d=m[6]*p.x+m[7]*p.y+m[8];return Point2((m[0]*p.x+m[1]*p.y+m[2])/d,(m[3]*p.x+m[4]*p.y+m[5])/d)}
}

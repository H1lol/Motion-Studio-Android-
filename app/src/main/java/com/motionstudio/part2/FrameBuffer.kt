package com.motionstudio.part2

import kotlin.math.floor

/** CPU reference RGBA framebuffer. Components are linearized Float values in [0,1]. */
class FrameBuffer(val width:Int,val height:Int,val pixels:FloatArray=FloatArray(validSize(width,height))) {
    init { require(pixels.size==validSize(width,height)) { "Pixel storage size mismatch" }; validateFinite() }
    companion object { private fun validSize(w:Int,h:Int):Int { require(w>0&&h>0) { "Invalid framebuffer dimensions" }; return Math.multiplyExact(Math.multiplyExact(w,h),4) } }
    private fun i(x:Int,y:Int):Int { require(x in 0 until width && y in 0 until height) { "Pixel out of bounds: $x,$y" }; return (y*width+x)*4 }
    fun get(x:Int,y:Int):Rgba { val k=i(x,y); return Rgba(pixels[k],pixels[k+1],pixels[k+2],pixels[k+3]) }
    fun set(x:Int,y:Int,c:Rgba) { val k=i(x,y); pixels[k]=c.r.sane(); pixels[k+1]=c.g.sane(); pixels[k+2]=c.b.sane(); pixels[k+3]=c.a.sane() }
    fun clear(c:Rgba=Rgba.TRANSPARENT) { var k=0; while(k<pixels.size){pixels[k]=c.r.sane();pixels[k+1]=c.g.sane();pixels[k+2]=c.b.sane();pixels[k+3]=c.a.sane();k+=4} }
    fun copy():FrameBuffer=FrameBuffer(width,height,pixels.copyOf())
    fun nearest(x:Float,y:Float,clamp:Boolean=true):Rgba { val xx=if(clamp)x.coerceIn(0f,(width-1).toFloat()) else x; val yy=if(clamp)y.coerceIn(0f,(height-1).toFloat()) else y; return get(floor(xx+0.5f).toInt().coerceIn(0,width-1),floor(yy+0.5f).toInt().coerceIn(0,height-1)) }
    fun bilinear(x:Float,y:Float):Rgba { val xx=x.coerceIn(0f,(width-1).toFloat()); val yy=y.coerceIn(0f,(height-1).toFloat()); val x0=floor(xx).toInt();val y0=floor(yy).toInt();val x1=(x0+1).coerceAtMost(width-1);val y1=(y0+1).coerceAtMost(height-1);val tx=xx-x0;val ty=yy-y0;val a=get(x0,y0);val b=get(x1,y0);val c=get(x0,y1);val d=get(x1,y1);return lerp(lerp(a,b,tx),lerp(c,d,tx),ty) }
    fun mapPixels(transform:(Rgba)->Rgba):FrameBuffer { val out=FrameBuffer(width,height); var k=0;while(k<pixels.size){val q=transform(Rgba(pixels[k],pixels[k+1],pixels[k+2],pixels[k+3]));out.pixels[k]=q.r.sane();out.pixels[k+1]=q.g.sane();out.pixels[k+2]=q.b.sane();out.pixels[k+3]=q.a.sane();k+=4};return out }
    fun validateFinite(){ for(v in pixels) require(v.isFinite()) { "Framebuffer contains NaN/Infinity" } }
}
data class Rgba(val r:Float,val g:Float,val b:Float,val a:Float){ companion object { val TRANSPARENT=Rgba(0f,0f,0f,0f);val WHITE=Rgba(1f,1f,1f,1f) } }
private fun Float.sane()=if(isFinite())coerceIn(0f,1f) else 0f
private fun lerp(a:Rgba,b:Rgba,t:Float)=Rgba(a.r+(b.r-a.r)*t,a.g+(b.g-a.g)*t,a.b+(b.b-a.b)*t,a.a+(b.a-a.a)*t)

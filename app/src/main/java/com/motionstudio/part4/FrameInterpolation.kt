package com.motionstudio.part4
object FrameInterpolation{
 fun blend(a:ByteArray,b:ByteArray,t:Float):ByteArray{require(a.size==b.size);val u=t.coerceIn(0f,1f);return ByteArray(a.size){i->(a[i].toInt()*(1-u)+b[i].toInt()*u).toInt().coerceIn(-128,127).toByte()}}
 fun interpolate(a:FloatArray,b:FloatArray,t:Float)=FloatArray(a.size){i->a[i]+(b[i]-a[i])*t.coerceIn(0f,1f)}
}

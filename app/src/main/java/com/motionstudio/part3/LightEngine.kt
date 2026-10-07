package com.motionstudio.part3
import com.motionstudio.part2.Rgba
data class Light(val id:String,val type:Type,val position:Vector3=Vector3(0.0,0.0,0.0),val direction:Vector3=Vector3(0.0,-1.0,0.0),val intensity:Double=1.0,val color:Rgba=Rgba.WHITE,val attenuation:Double=0.0){enum class Type{POINT,DIRECTIONAL,AMBIENT};init{require(intensity>=0&&attenuation>=0)}}

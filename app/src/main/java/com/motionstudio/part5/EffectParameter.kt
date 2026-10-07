package com.motionstudio.part5
data class EffectParameter(val id:String,val name:String,val type:ParameterType,val default:Any?,val min:Double?=null,val max:Double?=null,val keyframable:Boolean=true)
enum class ParameterType{FLOAT,INT,BOOL,COLOR,VECTOR2,VECTOR3,STRING,ENUM}

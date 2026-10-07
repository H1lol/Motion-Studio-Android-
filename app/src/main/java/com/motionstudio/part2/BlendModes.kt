package com.motionstudio.part2

enum class BlendMode { NORMAL,ADD,SCREEN,MULTIPLY,OVERLAY,SOFT_LIGHT,HARD_LIGHT,DIFFERENCE,EXCLUSION,DARKEN,LIGHTEN,COLOR_DODGE,COLOR_BURN }
object BlendModes {
    fun blend(src:Rgba,dst:Rgba,mode:BlendMode,opacity:Float=1f):Rgba {
        val o=opacity.coerceIn(0f,1f); val sa=(src.a*o).coerceIn(0f,1f); val da=dst.a.coerceIn(0f,1f)
        fun f(s:Float,d:Float)=when(mode){BlendMode.NORMAL->s;BlendMode.ADD->(s+d).coerceIn(0f,1f);BlendMode.SCREEN->1f-(1f-s)*(1f-d);BlendMode.MULTIPLY->s*d;BlendMode.OVERLAY->if(d<.5f)2f*s*d else 1f-2f*(1f-s)*(1f-d);BlendMode.HARD_LIGHT->if(s<.5f)2f*s*d else 1f-2f*(1f-s)*(1f-d);BlendMode.SOFT_LIGHT->if(s<.5f)d-(1f-2f*s)*d*(1f-d) else d+(2f*s-1f)*(g(d)-d);BlendMode.DIFFERENCE->kotlin.math.abs(d-s);BlendMode.EXCLUSION->d+s-2f*d*s;BlendMode.DARKEN->minOf(s,d);BlendMode.LIGHTEN->maxOf(s,d);BlendMode.COLOR_DODGE->if(s>=.999f)1f else (d/(1f-s)).coerceIn(0f,1f);BlendMode.COLOR_BURN->if(s<=.001f)0f else (1f-(1f-d)/s).coerceIn(0f,1f)}
        val br=f(src.r,dst.r);val bg=f(src.g,dst.g);val bb=f(src.b,dst.b);val outA=sa+da*(1f-sa)
        if(outA<=1e-8f)return Rgba.TRANSPARENT
        return Rgba((br*sa+dst.r*da*(1f-sa))/outA,(bg*sa+dst.g*da*(1f-sa))/outA,(bb*sa+dst.b*da*(1f-sa))/outA,outA)
    }
    private fun g(x:Float):Float=if(x<=.25f)(((16f*x-12f)*x+4f)*x) else kotlin.math.sqrt(x)
}

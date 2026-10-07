package com.motionstudio.part2
import kotlin.math.*
data class CompositeLayer(val frame:FrameBuffer,val opacity:Float=1f,val blend:BlendMode=BlendMode.NORMAL,val mask:FloatArray?=null,val transform:Transform2D?=null,val effects:EffectChain?=null)
class Compositor{fun composite(layers:List<CompositeLayer>,width:Int,height:Int):FrameBuffer{val out=FrameBuffer(width,height);for(layer in layers){var f=layer.frame;if(layer.effects!=null)f=layer.effects.render(f);if(layer.transform!=null)f=TransformRenderer.render(f,layer.transform,width,height);require(f.width==width&&f.height==height);for(y in 0 until height)for(x in 0 until width){val m=layer.mask?.getOrNull(y*width+x)?:1f;out.set(x,y,BlendModes.blend(f.get(x,y),out.get(x,y),layer.blend,layer.opacity*m))}};return out}}

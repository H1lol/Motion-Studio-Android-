package com.motionstudio.part3
data class TextRun(val text:String,val font:String="sans-serif",val size:Float=24f,val tracking:Float=0f,val kerning:Float=0f,val leading:Float=1.2f,val fill:Int=0xffffffff.toInt(),val stroke:Int=0,val strokeWidth:Float=0f,val shadow:TextShadow?=null)
data class TextShadow(val dx:Float,val dy:Float,val blur:Float,val color:Int)
data class TextLayerModel(val runs:List<TextRun>,val alignment:Alignment=Alignment.LEFT,val characterAnimation:CharacterAnimation?=null)
enum class Alignment{LEFT,CENTER,RIGHT,JUSTIFY}
data class CharacterAnimation(val properties:Set<String>,val seed:Long=0L)
object TextEngine{fun flatten(layer:TextLayerModel):String=layer.runs.joinToString(""){it.text};fun advance(run:TextRun):Float{require(run.size>0);return run.text.length*run.size*.55f+run.tracking*(run.text.length-1).coerceAtLeast(0)+run.kerning*(run.text.length-1).coerceAtLeast(0)} }

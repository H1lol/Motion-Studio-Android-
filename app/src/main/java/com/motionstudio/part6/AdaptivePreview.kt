package com.motionstudio.part6
import kotlin.math.max
import kotlin.math.min
data class PreviewSignals(val deviceScore:Double,val memoryPressure:Double,val renderComplexity:Double,val sourceWidth:Int,val sourceHeight:Int,val playing:Boolean)
data class PreviewQuality(val width:Int,val height:Int,val scale:Double,val frameSkip:Int)
class AdaptivePreview(private val maxWidth:Int=1920,private val maxHeight:Int=1080){fun choose(s:PreviewSignals):PreviewQuality{require(s.sourceWidth>0&&s.sourceHeight>0);val pressure=(1-s.memoryPressure.coerceIn(0.0,1.0));val score=(s.deviceScore.coerceIn(0.0,1.0)*.45+pressure*.25+(1-s.renderComplexity.coerceIn(0.0,1.0))*.30);var scale=when{score>.8->1.0;score>.6->.75;score>.4->.5;else->.25};if(s.playing)scale=min(scale,.75);val w=min(maxWidth,max(1,(s.sourceWidth*scale).toInt()));val h=min(maxHeight,max(1,(s.sourceHeight*scale).toInt()));return PreviewQuality(w,h,w.toDouble()/s.sourceWidth,if(score<.25&&s.playing)1 else 0)}}

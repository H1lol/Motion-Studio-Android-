package com.motionstudio.part5
sealed interface CopilotCommand{
 data class AddEffect(val layer:String,val effect:String):CopilotCommand
 data class RemoveEffect(val layer:String,val effect:String):CopilotCommand
 data class ChangeParameter(val effect:String,val parameter:String,val value:Double):CopilotCommand
 data class CreateKeyframe(val layer:String,val property:String,val timeUs:Long):CopilotCommand
 data class SplitLayer(val layer:String,val timeUs:Long):CopilotCommand
 data class TrimLayer(val layer:String,val startUs:Long,val endUs:Long):CopilotCommand
 data class AddTransition(val from:String,val to:String,val transition:String):CopilotCommand
 data class ImportAsset(val path:String):CopilotCommand
 data class SearchEffect(val query:String):CopilotCommand
 data class CreateComposition(val name:String,val width:Int,val height:Int,val durationUs:Long):CopilotCommand
}
class CopilotCommandParser{
 fun parse(text:String):CopilotCommand{val t=text.trim();val p=t.split(Regex("\\s+"));require(p.isNotEmpty());return when(p[0].lowercase()){"add"->if(p.getOrNull(1)?.equals("effect",true)==true)CopilotCommand.AddEffect(p.getOrElse(2){error("layer")},p.getOrElse(3){error("effect")})else error("Unsupported add command");"remove"->CopilotCommand.RemoveEffect(p.getOrElse(2){error("layer")},p.getOrElse(3){error("effect")});"search"->CopilotCommand.SearchEffect(p.drop(2).joinToString(" "));"import"->CopilotCommand.ImportAsset(p.drop(1).joinToString(" "));"split"->CopilotCommand.SplitLayer(p.getOrElse(1){error("layer")},p.getOrElse(2){error("timeUs")}.toLong());else->error("Unsupported command")}}
}

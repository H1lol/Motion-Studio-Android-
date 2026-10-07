package com.motionstudio.part5
class CopilotEngine(private val parser:CopilotCommandParser,private val registry:AssetRegistry){
 fun validate(c:CopilotCommand):Result<Unit>{return runCatching{when(c){is CopilotCommand.AddEffect->require(c.effect.isNotBlank());is CopilotCommand.ChangeParameter->require(c.parameter.isNotBlank()&&c.value.isFinite());is CopilotCommand.CreateKeyframe->require(c.timeUs>=0);is CopilotCommand.SplitLayer->require(c.timeUs>=0);is CopilotCommand.TrimLayer->require(c.startUs>=0&&c.endUs>=c.startUs);is CopilotCommand.CreateComposition->require(c.width>0&&c.height>0&&c.durationUs>=0);else->Unit}}}
 fun parseAndValidate(text:String)=parser.parse(text).let{it to validate(it)}
}

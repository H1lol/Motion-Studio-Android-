package com.motionstudio.part5
data class PluginCompatibilityResult(val compatibility:Compatibility,val mode:ExecutionMode)
object PluginCompatibility{
 fun classifyResult(i:BinaryInfo):PluginCompatibilityResult{
  val android=(i.platform.contains("Android",true)||i.platform.contains("Linux",true))&&i.architecture.contains("ARM64",true)
  val c=if(android&&i.executable)Compatibility("Android",i.architecture,true,"Candidate native binary; ABI and dependencies still require val idation") else Compatibility(i.platform,i.architecture,false,"Desktop or unknown binary cannot execute directly on Android")
  val mode=when{c.compatible->ExecutionMode.NATIVE;i.platform=="Windows"||i.platform=="macOS"->ExecutionMode.REBUILD_REQUIRED;else->ExecutionMode.UNSUPPORTED}
  return PluginCompatibilityResult(c,mode)
 }
 fun classify(i:BinaryInfo)=classifyResult(i).compatibility
}

package com.motionstudio.part5
data class Diagnostic(val severity:Severity,val code:String,val message:String,val asset:String?=null)
enum class Severity{INFO,WARNING,ERROR}
class DiagnosticsEngine{
 fun asset(a:AssetDescriptor):List<Diagnostic>{val d=mutableListOf<Diagnostic>();if(!a.compatibility.compatible)d+=Diagnostic(Severity.WARNING,"PLUGIN_PLATFORM","Asset is not directly compatible",a.id);if(a.dependencies.isNotEmpty())d+=Diagnostic(Severity.INFO,"DEPENDENCIES","Asset declares ${a.dependencies.size} dependencies",a.id);if(a.hash.isBlank())d+=Diagnostic(Severity.ERROR,"HASH","Asset hash is missing",a.id);return d}
 fun graph(g:AssetDependencyGraph)=g.cycles().map{Diagnostic(Severity.ERROR,"DEPENDENCY_CYCLE",it.joinToString(" -> "))}
}

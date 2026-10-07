package com.motionstudio.part6
import com.motionstudio.part3.Project
import com.motionstudio.part5.AssetDescriptor

enum class LintSeverity{INFO,WARNING,ERROR}
data class LintIssue(val severity:LintSeverity,val code:String,val message:String,val reference:String?=null)
class ProjectLinter{fun lint(project:Project,assets:List<AssetDescriptor>,dependencies:DependencyReport?=null):List<LintIssue>{val out=mutableListOf<LintIssue>();if(project.id.isBlank())out+=LintIssue(LintSeverity.ERROR,"PROJECT_ID","Project id is blank");project.compositions.forEach{if(it.width<=0||it.height<=0||it.fps<=0||it.duration<0)out+=LintIssue(LintSeverity.ERROR,"COMPOSITION_INVALID","Invalid composition ${it.id}",it.id)};assets.filter{!it.installed}.forEach{out+=LintIssue(LintSeverity.WARNING,"ASSET_MISSING","Asset is not installed: ${it.name}",it.id)};dependencies?.missing?.forEach{out+=LintIssue(LintSeverity.ERROR,"DEPENDENCY_MISSING","Missing ${it.type} ${it.id}",it.id)};dependencies?.cycles?.forEach{out+=LintIssue(LintSeverity.ERROR,"DEPENDENCY_CYCLE","Dependency cycle: ${it.joinToString(" -> ")}")};return out}}

package com.motionstudio.part5
data class Dependency(val id:String,val version:String,val type:String)
data class DependencyResolution(val resolved:Boolean,val missing:List<Dependency>,val conflicts:List<String>,val order:List<String>)
class PluginDependencyManager{
 fun resolve(root:String,deps:Map<String,List<Dependency>>):DependencyResolution{val visiting=mutableSetOf<String>();val done=mutableSetOf<String>();val order=mutableListOf<String>();val missing=mutableListOf<Dependency>();val conflicts=mutableListOf<String>();fun dfs(id:String){if(id in visiting){conflicts+="Dependency cycle at $id";return};if(id in done)return;visiting+=id;for(d in deps[id].orEmpty()){if(d.id !in deps)missing+=d else dfs(d.id)};visiting-=id;done+=id;order+=id};dfs(root);return DependencyResolution(missing.isEmpty()&&conflicts.isEmpty(),missing,conflicts,order)}
}

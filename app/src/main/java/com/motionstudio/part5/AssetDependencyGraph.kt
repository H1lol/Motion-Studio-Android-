package com.motionstudio.part5
class AssetDependencyGraph{
 private val edges=mutableMapOf<String,MutableSet<String>>()
 fun link(asset:String,dependency:String){edges.getOrPut(asset){mutableSetOf()}+=dependency}
 fun dependencies(id:String)=edges[id].orEmpty().toSet()
 fun cycles():List<List<String>>{val result=mutableListOf<List<String>>();val stack=mutableListOf<String>();val visiting=mutableSetOf<String>();val done=mutableSetOf<String>();fun dfs(v:String){if(v in visiting){val i=stack.indexOf(v);if(i>=0)result+=stack.subList(i,stack.size)+v;return};if(v in done)return;visiting+=v;stack+=v;edges[v].orEmpty().forEach(::dfs);stack.removeAt(stack.lastIndex);visiting-=v;done+=v};edges.keys.forEach(::dfs);return result}
}

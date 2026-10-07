package com.motionstudio.part6

data class DependencyRef(val id:String,val type:String,val version:String?=null)
data class DependencyReport(val resolved:List<DependencyRef>,val missing:List<DependencyRef>,val cycles:List<List<String>>)
class DependencyResolver(private val available:Map<String,DependencyRef>){
 fun resolve(requested:List<DependencyRef>,edges:Map<String,Set<String>> = emptyMap()):DependencyReport{
  val missing=mutableListOf<DependencyRef>(); val resolved=mutableListOf<DependencyRef>()
  requested.forEach{if(available[it.id]!=null) resolved+=available[it.id]!! else missing+=it}
  return DependencyReport(resolved.distinctBy{it.id},missing.distinctBy{it.id},cycles(edges))
 }
 private fun cycles(edges:Map<String,Set<String>>):List<List<String>>{
  val out=mutableListOf<List<String>>();val stack=mutableListOf<String>();val active=mutableSetOf<String>()
  fun dfs(n:String){if(n in active){out+=stack.dropWhile{it!=n}+n;return};active+=n;stack+=n;edges[n].orEmpty().forEach(::dfs);stack.removeAt(stack.lastIndex);active-=n}
  edges.keys.forEach(::dfs);return out.distinct()
 }
}

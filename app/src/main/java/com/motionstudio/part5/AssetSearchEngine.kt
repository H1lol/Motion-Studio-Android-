package com.motionstudio.part5
class AssetSearchEngine(private val registry:AssetRegistry){
 fun search(query:String,category:AssetCategory?=null,extension:String?=null,compatible:Boolean?=null,installed:Boolean?=null):List<AssetDescriptor>{val q=query.lowercase();return registry.all().asSequence().filter{category==null||it.category==category}.filter{extension==null||it.extension.equals(extension,true)}.filter{compatible==null||it.compatibility.compatible==compatible}.filter{installed==null||it.installed==installed}.map{it to fuzzy(it.name.lowercase(),q)}.filter{it.second>0}.sortedByDescending{it.second}.map{it.first}.toList()}
 private fun fuzzy(s:String,q:String):Int{if(q.isEmpty())return 1;if(s.contains(q))return 100-q.length;var i=0;var score=0;for(c in s)if(i<q.length&&c==q[i++])score++;return if(i==q.length)score else 0}
}

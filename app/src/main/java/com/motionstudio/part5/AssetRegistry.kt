package com.motionstudio.part5
class AssetRegistry{
 private val assets=LinkedHashMap<String,AssetDescriptor>()
 @Synchronized fun register(a:AssetDescriptor){require(a.id.isNotBlank()&&a.size>=0);assets[a.id]=a}
 @Synchronized fun remove(id:String)=assets.remove(id)
 fun get(id:String)=assets[id]
 fun all()=assets.values.toList()
 fun search(query:String):List<AssetDescriptor>{val q=query.trim().lowercase();if(q.isEmpty())return all();return assets.values.sortedBy{score(it,q)}.filter{score(it,q)>0}}
 private fun score(a:AssetDescriptor,q:String):Int{var s=0;if(a.name.lowercase()==q)s+=100;if(a.name.lowercase().contains(q))s+=50;if(a.filename.lowercase().contains(q))s+=20;if(a.category.name.lowercase().contains(q))s+=10;return s}
}

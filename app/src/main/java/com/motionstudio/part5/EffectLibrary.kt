package com.motionstudio.part5
class EffectLibrary(private val registry:AssetRegistry){
 fun byCategory(c:AssetCategory)=registry.all().filter{it.category==c}
 fun compatible()=registry.all().filter{it.compatibility.compatible}
 fun plugins()=registry.all().filter{it.category==AssetCategory.PLUGIN}
 fun favorites(ids:Set<String>)=registry.all().filter{it.id in ids}
 fun recent(ids:List<String>)=ids.mapNotNull(registry::get)
}

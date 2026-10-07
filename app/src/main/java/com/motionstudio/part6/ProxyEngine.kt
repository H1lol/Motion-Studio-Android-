package com.motionstudio.part6
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class MediaMode{SOURCE,PROXY,ADAPTIVE}
data class ProxySpec(val width:Int,val height:Int,val codec:String,val bitrate:Int)
data class ProxyBinding(val mediaId:String,val source:File,val proxy:File?,val spec:ProxySpec?)
interface ProxyGenerator{fun generate(source:File,destination:File,spec:ProxySpec,progress:(Double)->Unit):File}
class ProxyEngine(private val generator:ProxyGenerator){private val bindings=ConcurrentHashMap<String,ProxyBinding>();fun bindSource(id:String,source:File){bindings[id]=ProxyBinding(id,source,null,null)};fun createProxy(id:String,dest:File,spec:ProxySpec,progress:(Double)->Unit={}):File{val b=bindings[id]?:error("Unknown media $id");val p=generator.generate(b.source,dest,spec,progress);bindings[id]=b.copy(proxy=p,spec=spec);return p};fun resolve(id:String,mode:MediaMode,adaptive:Boolean=false):File{val b=bindings[id]?:error("Unknown media $id");return when(mode){MediaMode.SOURCE->b.source;MediaMode.PROXY->b.proxy?:error("Proxy missing for $id");MediaMode.ADAPTIVE->if(adaptive&&b.proxy!=null)b.proxy else b.source}};fun binding(id:String)=bindings[id]}

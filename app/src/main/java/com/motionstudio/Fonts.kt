package com.motionstudio.ui

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import android.net.Uri

/** Motion Studio font registry: bundled assets + user-imported fonts. */
data class MotionStudioFont(val id:String,val displayName:String,val assetPath:String,val extension:String,val familyName:String?=null,val weight:Int=400,val italic:Boolean=false)
class MotionStudioFontRegistry(private val context:Context){
    private val cache=mutableMapOf<String,Typeface>()
    fun scan():List<MotionStudioFont>{val out=mutableListOf<MotionStudioFont>();context.assets.list("fonts")?.forEach{walkAsset("fonts/$it",out)};File(context.filesDir,"fonts").apply{mkdirs()}.walkTopDown().filter{it.isFile&&it.extension.lowercase() in setOf("ttf","otf","ttc")}.forEach{out+=MotionStudioFont("user:${it.name}",it.name,it.absolutePath,it.extension)};return out.distinctBy{it.id}.sortedBy{it.displayName.lowercase()}}
    private fun walkAsset(path:String,out:MutableList<MotionStudioFont>){val children=context.assets.list(path) ?: return; if(children.isEmpty()){val ext=path.substringAfterLast('.',"").lowercase();if(ext in setOf("ttf","otf","ttc")){val n=path.substringAfterLast('/');out+=MotionStudioFont("asset:$path",n,path,ext)};return};children.forEach{walkAsset("$path/$it",out)}}
    fun search(q:String)=scan().filter{it.displayName.contains(q,true)||it.familyName?.contains(q,true)==true}
    fun typeface(font:MotionStudioFont):Typeface?=cache[font.id]?:runCatching{if(font.id.startsWith("asset:"))Typeface.createFromAsset(context.assets,font.assetPath) else Typeface.createFromFile(font.assetPath)}.getOrNull()?.also{cache[font.id]=it}
    fun importFont(uri:Uri):MotionStudioFont?=runCatching{val dir=File(context.filesDir,"fonts").apply{mkdirs()};val name=(uri.lastPathSegment?.substringAfterLast('/')?:"font.ttf").replace(Regex("[^A-Za-z0-9._-]"),"_");val target=File(dir,name);context.contentResolver.openInputStream(uri)!!.use{input->FileOutputStream(target).use{input.copyTo(it)}};scan().firstOrNull{it.assetPath==target.absolutePath}}.getOrNull()
}
object MotionStudioFonts { private var registry:MotionStudioFontRegistry?=null; fun get(context:Context)=registry ?: MotionStudioFontRegistry(context.applicationContext).also{registry=it} }

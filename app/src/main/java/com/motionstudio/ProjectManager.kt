package com.motionstudio.app

import android.content.Context
import com.motionstudio.part1.Project
import com.motionstudio.part6.StorageKind
import com.motionstudio.part6.StorageManager
import java.io.File

class ProjectManager(context:Context){
    private val root=File(context.filesDir,"motionstudio").apply{mkdirs()}
    val storage=StorageManager(root)
    fun projectFile(id:String)=storage.file(StorageKind.PROJECT,"$id.msproject")
    fun cacheDir()=storage.file(StorageKind.CACHE,".").apply{mkdirs()}
    fun ensureProjectSpace(bytes:Long)=storage.hasSpace(bytes,16L*1024L*1024L)
}

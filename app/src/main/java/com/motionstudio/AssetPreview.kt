package com.motionstudio.app

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import java.io.InputStream

class AssetPreview(private val resolver:ContentResolver){
    fun image(uri:Uri,maxSize:Int=512):Bitmap?=resolver.openInputStream(uri)?.use{decode(it,maxSize)}
    private fun decode(input:InputStream,max:Int):Bitmap? { val bytes=input.readBytes();val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,o);var s=1;while(o.outWidth/s>max||o.outHeight/s>max)s*=2;return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=s}) }
}

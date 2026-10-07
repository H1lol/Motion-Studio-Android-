package com.motionstudio.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Build
import android.media.MediaCodecList
import com.motionstudio.part2.*

object DeviceCapabilities {
    fun renderer(context:Context):RendererCapabilities {
        val am= context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val es=am.deviceConfigurationInfo.reqGlEsVersion
        val major=if(es>=0x30000)3 else if(es>=0x20000)2 else 0
        val vulkan=Build.VERSION.SDK_INT>=24 && context.packageManager.hasSystemFeature("android.hardware.vulkan.level")
        val max=if(major>=2){val a=IntArray(1); GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,a,0);a[0]}else 2048
        return RendererCapabilities(major>=2,major,vulkan,major>=3,Build.VERSION.SDK_INT>=24,max,if(major>=3)RenderBackendKind.OPENGL_ES else RenderBackendKind.CPU)
    }
    fun decoderCount():Int=MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.count{!it.isEncoder}
    fun supportsSurfaceView(context:Context)=context.packageManager.hasSystemFeature("android.hardware.opengles")
}

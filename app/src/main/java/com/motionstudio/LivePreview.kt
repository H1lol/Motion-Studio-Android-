package com.motionstudio.app

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.motionstudio.part2.FrameBuffer

class MotionStudioPreviewView(context:Context, private val renderer:GpuRenderer=GpuRenderer()):GLSurfaceView(context){
    init { setEGLContextClientVersion(2); setRenderer(renderer); renderMode=RENDERMODE_WHEN_DIRTY }
    fun submit(frame:FrameBuffer){queueEvent{renderer.submit(frame)};requestRender()}
    fun renderer()=renderer
}

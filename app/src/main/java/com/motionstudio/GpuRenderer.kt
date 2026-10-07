package com.motionstudio.app

import android.opengl.*
import com.motionstudio.part2.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Real OpenGL ES 2/3 preview backend. It uploads FrameBuffer data, renders through an FBO, and supports readback. */
class GpuRenderer : GpuBackend, GLSurfaceView.Renderer {
    override var capabilities: RendererCapabilities = RendererCapabilities(true,2,false,false,false,4096,RenderBackendKind.OPENGL_ES); private set
    private var program=0; private var texture=0; private var fbo=0; private var fboTex=0; private var width=1; private var height=1; private var pending:FrameBuffer?=null; private var latest:FrameBuffer?=null
    private val vertex = floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)
    private val vb:FloatBuffer=ByteBuffer.allocateDirect(vertex.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(vertex);position(0)}
    private val vs="attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){gl_Position=vec4(aPos,0.0,1.0);vUv=aUv;}"
    private val fs="precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){gl_FragColor=texture2D(uTex,vUv);}"
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){ GLES20.glDisable(GLES20.GL_DEPTH_TEST); program=link(vs,fs); texture=genTexture(); capabilities=capabilities.copy(openGlEsMajor=if(GLES20.glGetString(GLES20.GL_VERSION).contains("OpenGL ES 3"))3 else 2,maxTextureSize=queryMax()) }
    override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){width=w.coerceAtLeast(1);height=h.coerceAtLeast(1);createFbo(width,height)}
    override fun onDrawFrame(gl:GL10?){val src= synchronized(this){pending?.also{pending=null}} ?: return; upload(src); GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo); GLES20.glViewport(0,0,width,height); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); draw(); latest=readback(width,height); GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)}
    fun submit(frame:FrameBuffer){synchronized(this){pending=frame}}
    fun latestFrame():FrameBuffer?=synchronized(this){latest?.copy()}
    override fun render(source:FrameBuffer,graph:RenderGraph<FrameBuffer>?):FrameBuffer { val f=graph?.execute()?.values?.lastOrNull()?:source; submit(f); return f.copy() }
    private fun queryMax():Int{val a=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,a,0);return a[0]}
    private fun genTexture():Int{val a=IntArray(1);GLES20.glGenTextures(1,a,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,a[0]);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);return a[0]}
    private fun upload(f:FrameBuffer){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);val b=ByteBuffer.allocate(f.width*f.height*4).order(ByteOrder.nativeOrder());for(y in 0 until f.height)for(x in 0 until f.width){val c=f.get(x,y);b.put((c.r.coerceIn(0f,1f)*255).toInt().toByte());b.put((c.g.coerceIn(0f,1f)*255).toInt().toByte());b.put((c.b.coerceIn(0f,1f)*255).toInt().toByte());b.put((c.a.coerceIn(0f,1f)*255).toInt().toByte())};b.position(0);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,f.width,f.height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,b)}
    private fun draw(){GLES20.glUseProgram(program);val p=GLES20.glGetAttribLocation(program,"aPos");val u=GLES20.glGetAttribLocation(program,"aUv");vb.position(0);GLES20.glEnableVertexAttribArray(p);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vb);vb.position(2);GLES20.glEnableVertexAttribArray(u);GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,16,vb);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(u)}
    private fun readback(w:Int,h:Int):FrameBuffer{val b=ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.nativeOrder());GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,b);val o=FrameBuffer(w,h);for(y in 0 until h)for(x in 0 until w){val i=((h-1-y)*w+x)*4;b.position(i);o.set(x,y,Rgba((b.get().toInt() and 255)/255f,(b.get().toInt() and 255)/255f,(b.get().toInt() and 255)/255f,(b.get().toInt() and 255)/255f))};return o}
    private fun createFbo(w:Int,h:Int){if(fbo!=0)GLES20.glDeleteFramebuffers(1,intArrayOf(fbo),0);if(fboTex!=0)GLES20.glDeleteTextures(1,intArrayOf(fboTex),0);val t=genTexture();fboTex=t;GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,t);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null as ByteBuffer?);val a=IntArray(1);GLES20.glGenFramebuffers(1,a,0);fbo=a[0];GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,t,0);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)}
    private fun compile(type:Int,src:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetShaderInfoLog(s)};return s}
    private fun link(v:String,f:String):Int{val p=GLES20.glCreateProgram();GLES20.glAttachShader(p,compile(GLES20.GL_VERTEX_SHADER,v));GLES20.glAttachShader(p,compile(GLES20.GL_FRAGMENT_SHADER,f));GLES20.glLinkProgram(p);val ok=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetProgramInfoLog(p)};return p}
}

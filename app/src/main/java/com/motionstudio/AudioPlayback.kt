package com.motionstudio.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.motionstudio.part4.AudioEngine

class AudioPlayback(private val sampleRate:Int=48000) : AutoCloseable {
    private var track:AudioTrack?=null
    private val engine=AudioEngine()
    fun start(){ if(track!=null)return; val min=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT); track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes((min.coerceAtLeast(sampleRate*4))).setTransferMode(AudioTrack.MODE_STREAM).build().also{it.play()} }
    fun write(clips:List<com.motionstudio.part4.AudioClip>,samples:Int){start();val data=engine.mix(clips,samples);track?.write(data,0,data.size,AudioTrack.WRITE_BLOCKING)}
    fun pause(){track?.pause()}
    fun flush(){track?.flush()}
    override fun close(){track?.stop();track?.release();track=null}
}

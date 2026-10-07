package com.motionstudio.part4
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
data class TrackInfo(val index:Int,val mime:String,val durationUs:Long,val width:Int,val height:Int,val sampleRate:Int,val channels:Int,val rotation:Int)
class MediaExtractorAdapter(private val context:Context):AutoCloseable{
 private var extractor:MediaExtractor?=null
 fun open(uri:Uri):List<TrackInfo>{close();val e=MediaExtractor();try{e.setDataSource(context,uri,null);extractor=e;return (0 until e.trackCount).map{ i->val f=e.getTrackFormat(i);TrackInfo(i,f.getString(MediaFormat.KEY_MIME).orEmpty(),f.getLongOrZero(MediaFormat.KEY_DURATION),f.getIntOrZero(MediaFormat.KEY_WIDTH),f.getIntOrZero(MediaFormat.KEY_HEIGHT),f.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE),f.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT),f.getIntOrZero(MediaFormat.KEY_ROTATION))}}catch(t:Throwable){e.release();throw MediaAdapterException("Unable to inspect media",t)}}
 fun selectTrack(index:Int){require(index>=0);extractor?.selectTrack(index)?:error("Extractor is closed")}
 fun seekTo(timeUs:Long){extractor?.seekTo(timeUs.coerceAtLeast(0),MediaExtractor.SEEK_TO_CLOSEST_SYNC)?:error("Extractor is closed")}
 fun readSample(maxBytes:Int=4*1024*1024):ByteArray?{require(maxBytes>0);val e=extractor?:error("Extractor is closed");val n=e.sampleSize;if(n<0)return null;if(n>maxBytes)throw MediaAdapterException("Sample exceeds safety limit");val b=ByteArray(n.toInt());val read=e.readSampleData(java.nio.ByteBuffer.wrap(b),0);if(read<0)return null else return b.copyOf(read)}
 fun sampleTimeUs()=extractor?.sampleTime?:-1L
 override fun close(){extractor?.release();extractor=null}
}
private fun MediaFormat.getLongOrZero(k:String)=runCatching{if(containsKey(k))getLong(k)else 0L}.getOrDefault(0)
private fun MediaFormat.getIntOrZero(k:String)=runCatching{if(containsKey(k))getInteger(k)else 0}.getOrDefault(0)
class MediaAdapterException(message:String,cause:Throwable?=null):Exception(message,cause)

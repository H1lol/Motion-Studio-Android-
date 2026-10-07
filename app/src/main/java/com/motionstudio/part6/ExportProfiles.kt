package com.motionstudio.part6

enum class VideoCodec(val mime:String){ H264("video/avc"), HEVC("video/hevc"), VP8("video/x-vnd.on2.vp8"), VP9("video/x-vnd.on2.vp9"), AV1("video/av01") }
enum class AudioCodec(val mime:String){ AAC("audio/mp4a-latm"), OPUS("audio/opus"), AMR_NB("audio/3gpp"), AMR_WB("audio/amr-wb") }
enum class Container(val extension:String){ MP4("mp4"), WEBM("webm"), THREE_GPP("3gp") }
enum class PixelFormat{ YUV420, YUV420_10BIT, RGBA, RGB }
enum class Quality{ DRAFT, STANDARD, HIGH, LOSSLESS }
data class ExportProfile(val id:String,val name:String,val width:Int,val height:Int,val fps:Double,val videoCodec:VideoCodec,val bitrate:Int,val pixelFormat:PixelFormat,val audioCodec:AudioCodec?,val audioBitrate:Int?,val container:Container,val quality:Quality,val alpha:Boolean){init{require(width>0&&height>0&&fps>0&&bitrate>0);require(audioBitrate==null||audioBitrate>0)}}
data class CodecCapability(val codec:VideoCodec,val encoderName:String,val hardware:Boolean,val pixelFormats:Set<PixelFormat>,val maxWidth:Int,val maxHeight:Int,val maxFps:Double,val containers:Set<Container>)
data class AudioCodecCapability(val codec:AudioCodec,val encoderName:String,val hardware:Boolean,val containers:Set<Container>)
data class ValidatedExport(val profile:ExportProfile,val video:CodecCapability,val audio:AudioCodecCapability?)
object ExportProfiles{
 val SOCIAL=ExportProfile("social","Social Video",1080,1920,30.0,VideoCodec.H264,8_000_000,PixelFormat.YUV420,AudioCodec.AAC,192_000,Container.MP4,Quality.HIGH,false)
 val MASTER=ExportProfile("master","High Quality Master",3840,2160,60.0,VideoCodec.HEVC,40_000_000,PixelFormat.YUV420,AudioCodec.AAC,320_000,Container.MP4,Quality.LOSSLESS,false)
 val PREVIEW=ExportProfile("preview","Preview",1280,720,30.0,VideoCodec.H264,4_000_000,PixelFormat.YUV420,AudioCodec.AAC,128_000,Container.MP4,Quality.DRAFT,false)
 val MOBILE=ExportProfile("mobile","Mobile Friendly",1280,720,30.0,VideoCodec.H264,5_000_000,PixelFormat.YUV420,AudioCodec.AAC,128_000,Container.MP4,Quality.STANDARD,false)
 fun transparent(width:Int=1920,height:Int=1080,fps:Double=30.0)=ExportProfile("transparent","Transparent Video",width,height,fps,VideoCodec.HEVC,12_000_000,PixelFormat.RGBA,null,null,Container.MP4,Quality.HIGH,true)
 fun validate(p:ExportProfile,v:List<CodecCapability>,a:List<AudioCodecCapability>):ValidatedExport{val vc=v.firstOrNull{it.codec==p.videoCodec&&p.width<=it.maxWidth&&p.height<=it.maxHeight&&p.fps<=it.maxFps&&p.pixelFormat in it.pixelFormats&&p.container in it.containers}?:error("Unsupported video combination: ${p.videoCodec}/${p.pixelFormat}/${p.container}"); if(p.alpha&&p.pixelFormat!=PixelFormat.RGBA)error("Alpha requires an alpha-capable pixel format"); val ac=p.audioCodec?.let{c->a.firstOrNull{it.codec==c&&p.container in it.containers}?:error("Unsupported audio/container combination")}; return ValidatedExport(p,vc,ac)}
}

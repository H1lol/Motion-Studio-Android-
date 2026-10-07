package com.motionstudio.part5
import java.io.File
object FormatRegistry{
 private val map=mapOf("aep" to AssetCategory.AE_ASSET,"aex" to AssetCategory.PLUGIN,"ffx" to AssetCategory.AE_ASSET,"jsx" to AssetCategory.SCRIPT,"mbr" to AssetCategory.MOTION_BRO,"plugin" to AssetCategory.PLUGIN,"dll" to AssetCategory.NATIVE_EXTENSION,"so" to AssetCategory.NATIVE_EXTENSION,"dylib" to AssetCategory.NATIVE_EXTENSION,"ofx" to AssetCategory.PLUGIN,"vst" to AssetCategory.PLUGIN,"vst3" to AssetCategory.PLUGIN,"glsl" to AssetCategory.SHADER,"spv" to AssetCategory.SHADER,"cube" to AssetCategory.LUT,"zip" to AssetCategory.PACKAGE,"7z" to AssetCategory.PACKAGE,"rar" to AssetCategory.PACKAGE,"mp4" to AssetCategory.MEDIA,"mov" to AssetCategory.MEDIA,"mkv" to AssetCategory.MEDIA,"webm" to AssetCategory.MEDIA,"png" to AssetCategory.MEDIA,"jpg" to AssetCategory.MEDIA,"jpeg" to AssetCategory.MEDIA,"webp" to AssetCategory.MEDIA,"gif" to AssetCategory.MEDIA,"wav" to AssetCategory.MEDIA,"mp3" to AssetCategory.MEDIA,"aac" to AssetCategory.MEDIA,"m4a" to AssetCategory.MEDIA,"flac" to AssetCategory.MEDIA)
 fun category(file:File)=map[file.extension.lowercase()]?:AssetCategory.UNKNOWN
 fun supported(file:File)=map.containsKey(file.extension.lowercase())
}

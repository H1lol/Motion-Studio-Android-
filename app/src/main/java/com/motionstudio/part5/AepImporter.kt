package com.motionstudio.part5
import java.io.File
data class AepScan(val compositions:List<String>,val layers:List<String>,val effectReferences:List<String>,val assetReferences:List<String>,val metadata:Map<String,String>,val warnings:List<String>)
class AepImporter{
 fun scan(file:File):AepScan{require(file.isFile);val bytes=file.readBytes();val text=String(bytes,Charsets.ISO_8859_1);val strings=Regex("[\\x20-\\x7E]{4,}").findAll(text).map{it.value}.toList();val comps=strings.filter{it.contains("composition",true)||it.contains("comp",true)}.distinct().take(500);val effects=strings.filter{it.contains("effect",true)||it.contains("plugin",true)}.distinct().take(500);val refs=strings.filter{it.matches(Regex(".*\\.(png|jpg|jpeg|mov|mp4|wav|aep|ffx)$",RegexOption.IGNORE_CASE))}.distinct();return AepScan(comps,strings.filter{it.contains("layer",true)}.distinct().take(1000),effects,refs, mapOf("format" to "Adobe After Effects project (binary inspection only)","size" to file.length().toString()),listOf("AEP is proprietary; this scanner does not claim full project reconstruction."))}
}

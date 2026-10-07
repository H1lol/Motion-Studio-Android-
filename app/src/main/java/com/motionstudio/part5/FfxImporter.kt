package com.motionstudio.part5
import java.io.File
data class FfxScan(val strings:List<String>,val parameters:List<String>,val warnings:List<String>)
class FfxImporter{fun scan(file:File):FfxScan{require(file.isFile);val text=String(file.readBytes(),Charsets.ISO_8859_1);val s=Regex("[\\x20-\\x7E]{4,}").findAll(text).map{it.value}.distinct().take(1000).toList();return FfxScan(s,s.filter{it.contains("Slider",true)||it.contains("Color",true)||it.contains("Opacity",true)},listOf("FFX is parsed only for accessible binary strings; full preset reconstruction is not claimed."))}}

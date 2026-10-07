package com.motionstudio.part5
import java.io.File
data class JsxAnalysis(val commands:List<String>,val reads:List<String>,val writes:List<String>,val warnings:List<String>)
class JsxImporter{fun analyze(file:File):JsxAnalysis{val s=file.readText();val commands=Regex("app\\.project|executeCommand|layers?\\.add|property\\(|setValue|setValuesAtTimes").findAll(s).map{it.value}.distinct().toList();val reads=Regex("File\\(|open\\(|read").findAll(s).map{it.value}.distinct().toList();val writes=Regex("save\\(|write|remove\\(|deleteFile").findAll(s).map{it.value}.distinct().toList();return JsxAnalysis(commands,reads,writes,listOf("JSX is analyzed, not executed as JavaScript. Compatible operations must be translated into Motion Studio commands."))}}

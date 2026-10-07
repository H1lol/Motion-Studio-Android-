package com.motionstudio.part6
data class MigrationResult(val version:Int,val data:Map<String,String>,val warnings:List<String>)
interface ProjectMigrationStep{val from:Int;val to:Int;fun migrate(data:MutableMap<String,String>):List<String>}
class ProjectMigration(private val currentVersion:Int,steps:List<ProjectMigrationStep>){private val byFrom=steps.associateBy{it.from};init{require(steps.map{it.from}.distinct().size==steps.size)};fun migrate(version:Int,input:Map<String,String>):MigrationResult{require(version<=currentVersion);val d=input.toMutableMap();val warnings=mutableListOf<String>();var v=version;while(v<currentVersion){val s=byFrom[v]?:error("No migration registered from version $v");warnings+=s.migrate(d);v=s.to};return MigrationResult(v,d,warnings)}}

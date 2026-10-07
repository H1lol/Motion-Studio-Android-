package com.motionstudio.part5
class PluginSandbox{
 data class Permission(val filesystem:Boolean=false,val network:Boolean=false,val gpu:Boolean=false)
 private val permissions=mutableMapOf<String,Permission>()
 fun grant(id:String,p:Permission){permissions[id]=p}
 fun permission(id:String)=permissions[id]?:Permission()
 fun canLoadNative(id:String)=permission(id).gpu||permission(id).filesystem
 fun revoke(id:String){permissions.remove(id)}
}

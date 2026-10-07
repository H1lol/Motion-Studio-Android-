package com.motionstudio.part5
import java.io.File
import java.nio.ByteBuffer
data class BinaryInfo(val signature:String,val platform:String,val architecture:String,val dependencies:List<String>,val version:String,val hash:String,val executable:Boolean)
object PluginBinaryLoader{
 fun inspect(file:File):BinaryInfo{require(file.isFile);val h=file.inputStream().use{java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes())}.joinToString(""){ "%02x".format(it)};val b=file.inputStream().use{it.readNBytes(32)};val sig=when{b.startsWith(byteArrayOf(0x7f,0x45,0x4c,0x46))->"ELF";b.startsWith(byteArrayOf(0x4d,0x5a))->"PE";b.startsWith(byteArrayOf(0xca.toByte(),0xfe.toByte(),0xba.toByte(),0xbe.toByte()))->"Mach-O/Universal";b.startsWith(byteArrayOf(0xcf.toByte(),0xfa.toByte(),0xed.toByte(),0xfe.toByte()))->"Mach-O";else->"Unknown"};val arch=if(sig.startsWith("ELF")){val cls=b.getOrNull(4)?.toInt();if(cls==2)"x86_64/ARM64 candidate" else "32-bit candidate"}else "unknown";val platform=when(sig){"ELF"->"Linux/Android candidate";"PE"->"Windows";else->"unknown"};return BinaryInfo(sig,platform,arch,emptyList(),"unknown",h,sig!="Unknown")}
 private fun ByteArray.startsWith(x:ByteArray)=size>=x.size&&x.indices.all{this[it]==x[it]}
}

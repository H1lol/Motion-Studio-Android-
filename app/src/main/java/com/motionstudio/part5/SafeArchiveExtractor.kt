package com.motionstudio.part5
import java.io.*
import java.util.zip.ZipFile
object SafeArchiveExtractor{
 fun extractZip(zip:File,out:File,maxFiles:Int=10000,maxBytes:Long=2L*1024*1024*1024):List<File>{require(zip.isFile&&out.mkdirs().let{true});var total=0L;val written=mutableListOf<File>();ZipFile(zip).use{z->require(z.size()<=maxFiles){"Too many entries"};for(e in z.entries()){require(!e.name.startsWith("/")&&!e.name.contains("..")){"Unsafe archive path"};if(e.isDirectory)continue;total+=e.size.coerceAtLeast(0);require(total<=maxBytes){"Archive exceeds extraction limit"};val f=File(out,e.name);f.parentFile?.mkdirs();z.getInputStream(e).use{input->f.outputStream().use{input.copyTo(it)}};written+=f}};return written}
}

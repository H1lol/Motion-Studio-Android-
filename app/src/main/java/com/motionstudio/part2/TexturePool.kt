package com.motionstudio.part2
class TexturePool(private val budgetBytes:Long){data class Key(val w:Int,val h:Int,val channels:Int=4);data class Handle(val id:Long,val key:Key,val bytes:Long);private val free=mutableMapOf<Key,MutableList<Handle>>();private val used=mutableMapOf<Long,Handle>();private var next=1L;var allocatedBytes=0L;private set
 @Synchronized fun acquire(key:Key):Handle{require(key.w>0&&key.h>0&&key.channels in 1..4);val b=Math.multiplyExact(Math.multiplyExact(key.w.toLong(),key.h.toLong()),key.channels);val h=free[key]?.removeLastOrNull();if(h!=null){used[h.id]=h;return h};require(b<=budgetBytes);evictUntil(b);val n=Handle(next++,key,b);used[n.id]=n;allocatedBytes+=b;return n}
 @Synchronized fun release(h:Handle){if(used.remove(h.id)!=null)free.getOrPut(h.key){mutableListOf()}.add(h)}
 @Synchronized fun evictUntil(required:Long){require(required>=0);while(allocatedBytes+required>budgetBytes){val e=free.entries.firstOrNull{it.value.isNotEmpty()}?:break;val h=e.value.removeLast();allocatedBytes-=h.bytes;if(e.value.isEmpty())free.remove(e.key)}}
}

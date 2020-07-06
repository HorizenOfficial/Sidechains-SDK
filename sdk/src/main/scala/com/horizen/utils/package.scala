package com.horizen

package object utils {
  private [horizen] implicit def byteArrayToWrapper(byte: Array[Byte]): ByteArrayWrapper = new ByteArrayWrapper(byte)

  private [horizen] implicit def wrapperToByteArray(byteArrayWrapper: ByteArrayWrapper): Array[Byte] = byteArrayWrapper.data

  private [horizen] implicit def byteArraySeqToWrapperSeq(bytes: Seq[Array[Byte]]): Seq[ByteArrayWrapper] = bytes.map(byteArrayToWrapper)

  class LruCache[K, V](val cacheSize: Int) extends java.util.LinkedHashMap[K, V] {
    override def removeEldestEntry(entry: java.util.Map.Entry[K, V]): Boolean = cacheSize < size()
  }
}

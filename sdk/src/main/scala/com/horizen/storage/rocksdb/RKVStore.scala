package com.horizen.storage.rocksdb

import com.horizen.common.interfaces.{DefaultReader, Reader}
import com.horizen.storageVersioned.StorageVersioned

import scala.collection.mutable
import scala.compat.java8.OptionConverters.RichOptionalGeneric

/**
 * That source code had been copied/modified from ErgoPlatform Project
 */

trait RKVStore extends AutoCloseable {

  type K = Array[Byte]
  type V = Array[Byte]

  protected val db: StorageVersioned

  def get(key: K): Option[V] =
    db.get(key).asScala

  def getAll(cond: (K, V) => Boolean): Seq[(K, V)] = {
    val iter = db.asInstanceOf[DefaultReader].getIter()
    try {
      val bf = mutable.ArrayBuffer.empty[(K, V)]
      var hasNext = true
      while (hasNext) {
        val next = iter.next()
        if (next.isPresent) {
          val key = next.get().getKey
          val value = next.get().getValue
          if (cond(key, value)) bf += (key -> value)
        }
        else
          hasNext = false
      }
      bf.toList
    } finally {
      iter.close()
    }
  }

  def getAll: Seq[(K, V)] = getAll((_, _) => true)

  def getOrElse(key: K, default: => V): V =
    get(key).getOrElse(default)

  def get(keys: Seq[K]): Seq[(K, Option[V])] = {
    val bf = mutable.ArrayBuffer.empty[(K, Option[V])]
    keys.foreach(k => bf += (k -> get(k)))
    bf
  }

  override def close(): Unit = db.close()

}

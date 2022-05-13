package com.horizen.storage
import java.util
import java.util.{Optional, List => JList}

import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.OptionConverters._

/* @TODO Discuss:
* 1. Storage interface changing is required. That storage is not support rollbacks, but it ok for current usages
* */
class InMemoryStorageAdapter(hashMap: mutable.HashMap[ByteArrayWrapper, ByteArrayWrapper] = mutable.HashMap()) extends Storage /*in fact it is non-versioned storage*/{
  override def get(key: ByteArrayWrapper): Optional[ByteArrayWrapper] = hashMap.get(key).asJava

  override def getOrElse(key: ByteArrayWrapper, defaultValue: ByteArrayWrapper): ByteArrayWrapper = hashMap.getOrElse(key, defaultValue)

  override def get(keys: JList[ByteArrayWrapper]): JList[JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]]] = keys.asScala.map(k => new JPair(k, get(k))).asJava

  override def getAll: util.List[JPair[ByteArrayWrapper, ByteArrayWrapper]] = hashMap.map{case (key, value) => new JPair(key, value)}.toSeq.asJava

  override def lastVersionID(): Optional[ByteArrayWrapper] = ???

  override def update(version: ByteArrayWrapper, toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]], toRemove: JList[ByteArrayWrapper]): Unit = {
    toRemove.asScala.map(keyToRemove => hashMap.remove(keyToRemove))
    toUpdate.asScala.map(pair => hashMap.put(pair.getKey, pair.getValue))
  }

  override def rollback(versionID: ByteArrayWrapper): Unit = ???

  override def rollbackVersions(): util.List[ByteArrayWrapper] = ???

  override def isEmpty: Boolean = hashMap.isEmpty

  override def close(): Unit = {}

  def copy(): InMemoryStorageAdapter = new InMemoryStorageAdapter(hashMap.clone())
}

package com.horizen.storage.leveldb

import java.io.File
import java.util
import java.util.{Optional, List => JList}

import com.horizen.storage.Storage
import com.horizen.storage.leveldb.LDBFactory.factory
import com.horizen.utils.{Pair => JPair, _}
import org.iq80.leveldb.Options

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._


/*
*  @TODO to discuss
*    1. Why we use ByteArrayWrapper instead of Array[Byte]?
*    2. We need iterator over the storage
* */
class VersionedLevelDbStorageAdapter(pathToDB: String, keepVersions: Int) extends Storage{
  private val dataBase: VersionedLDBKVStore = createDb(pathToDB)

  override def get(key: ByteArrayWrapper): Optional[ByteArrayWrapper] = dataBase.get(key).map(byteArrayToWrapper).asJava

  override def getOrElse(key: ByteArrayWrapper, defaultValue: ByteArrayWrapper): ByteArrayWrapper = dataBase.getOrElse(key, defaultValue)

  override def get(keys: JList[ByteArrayWrapper]): JList[JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]]] = {
    dataBase.get(keys.asScala.map(_.data))
      .map{case (key, value) =>
        new JPair(byteArrayToWrapper(key), value.map(v => byteArrayToWrapper(v)).asJava)}
      .asJava
  }

  override def getAll: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = {
    dataBase.getAll
      .map{case (key, value) => new JPair(byteArrayToWrapper(key), byteArrayToWrapper(value))}
      .asJava
  }

  override def lastVersionID(): Optional[ByteArrayWrapper] = dataBase.versions.lastOption.map(byteArrayToWrapper).asJava

  override def update(version: ByteArrayWrapper, toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]], toRemove: util.List[ByteArrayWrapper]): Unit = {

    val toUpdateAsScala = toUpdate.asScala.toList
    val toRemoveAsScala = toRemove.asScala.toList

    //key for storing version shall not be used as key in any key-value pair in VersionedLDBKVStore
    require(!toUpdateAsScala.exists(pair => pair.getKey == version) && !toRemoveAsScala.contains(version))

    val convertedToUpdate = toUpdateAsScala.map(pair => (pair.getKey.data, pair.getValue.data))
    val convertedToRemove = toRemoveAsScala.map(_.data)
    dataBase.update(convertedToUpdate, convertedToRemove)(version)
  }

  override def rollback(versionID: ByteArrayWrapper): Unit = dataBase.rollbackTo(versionID)

  override def rollbackVersions(): JList[ByteArrayWrapper] = dataBase.versions.map(byteArrayToWrapper).asJava

  override def close(): Unit = dataBase.close()

  def createDb(path: String): VersionedLDBKVStore = {
    val dir = new File(path)
    dir.mkdirs()
    val options = new Options()
    options.createIfMissing(true)
    val db = factory.open(dir, options)
    new VersionedLDBKVStore(db, keepVersions)
  }

  override def isEmpty: Boolean = dataBase.versions.isEmpty
}

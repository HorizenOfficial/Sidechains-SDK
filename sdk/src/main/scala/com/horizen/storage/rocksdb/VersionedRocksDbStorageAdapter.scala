package com.horizen.storage.rocksdb

import akka.japi.Option.scala2JavaOption
import com.horizen.storage.Storage
import com.horizen.storageVersioned.StorageVersioned
import com.horizen.utils.{Pair => JPair, _}
import scorex.util.ScorexLogging

import java.io.File
import java.util
import java.util.{Optional, List => JList}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8




/*
* */
class VersionedRocksDbStorageAdapter(pathToDB: File) extends Storage with ScorexLogging {
  private val versionsToKeep: Int = 720 * 2 + 1; //How many version could be saved at all, currently hardcoded to two consensus epochs length + 1
  private val dataBase: VersionedRDBKVStore = createDb(pathToDB)
//  private val versionsKey: ByteArrayWrapper = new ByteArrayWrapper(dataBase.VersionsKey)

  override def get(key: ByteArrayWrapper): Optional[ByteArrayWrapper] = dataBase.get(key).asScala.map(byteArrayToWrapper).asJava

  override def getOrElse(key: ByteArrayWrapper, defaultValue: ByteArrayWrapper): ByteArrayWrapper = dataBase.getOrElse(key, defaultValue)

  override def get(keys: JList[ByteArrayWrapper]): JList[JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]]] = {
    val tmp: Seq[Array[Byte]] = keys.asScala.map(_.data())
    dataBase.get(tmp)
      .map{case (key, value) =>
        new JPair(byteArrayToWrapper(key), value.asScala.map(v => byteArrayToWrapper(v)).asJava)}.toList
      .asJava
   }

  override def getAll: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = {

    dataBase.getAll
        .view
        .map{case (key, value) => (byteArrayToWrapper(key), byteArrayToWrapper(value))}
        .map{case (key, value) => new JPair(byteArrayToWrapper(key), byteArrayToWrapper(value))}
        .asJava
  }

  override def lastVersionID(): Optional[ByteArrayWrapper] = dataBase.versions.lastOption.map(byteArrayToWrapper).asJava

  override def update(version: ByteArrayWrapper, toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]], toRemove: util.List[ByteArrayWrapper]): Unit = {

    val toUpdateAsScala = toUpdate.asScala.toList
    val toRemoveAsScala = toRemove.asScala.toList

    // TODO is this still valid for RocksDb?
    // key for storing version shall not be used as key in any key-value pair in VersionedLDBKVStore
    require(!toUpdateAsScala.exists(pair => pair.getKey == version) && !toRemoveAsScala.contains(version))

    //@TODO added for compatibility with LSMStore, probably interface shall be changed to work with map collection for toUpdate/toremove
    require(toUpdateAsScala.map(_.getKey).toSet.size == toUpdateAsScala.size, "duplicate key in `toUpdate`")
    require(toRemoveAsScala.toSet.size == toRemoveAsScala.size, "duplicate key in `toRemove`")

    require(!isVersionExist(version), s"Version ${BytesUtils.toHexString(version)} already exists in storage")

    val convertedToUpdate = toUpdateAsScala.map(pair => (pair.getKey.data, pair.getValue.data))
    val convertedToRemove = toRemoveAsScala.map(_.data)
    dataBase.update(convertedToUpdate, convertedToRemove)(version)
  }

  private def isVersionExist(versionForSearch: ByteArrayWrapper): Boolean = {
    dataBase.versions.map(byteArrayToWrapper).contains(versionForSearch)
  }

  override def rollback(versionID: ByteArrayWrapper): Unit = {
    if (isVersionExist(versionID)) {
      dataBase.rollbackTo(versionID)
    }
    else {
      throw new IllegalArgumentException("Rollback to non exist version")
    }
  }

  override def rollbackVersions(): JList[ByteArrayWrapper] = dataBase.versions.map(byteArrayToWrapper).asJava

  override def close(): Unit = dataBase.close()

  private def createDb(path: File): VersionedRDBKVStore= {
    val db = StorageVersioned.open(path.getAbsolutePath, true, versionsToKeep)
    new VersionedRDBKVStore(db, versionsToKeep)
  }

  override def isEmpty: Boolean = dataBase.versions.isEmpty
}

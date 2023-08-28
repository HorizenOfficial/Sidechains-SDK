package io.horizen.storage.leveldb


import java.io.File
import java.util
import java.util.{Optional, List => JList}
import io.horizen.storage.{Storage, StorageIterator}
import io.horizen.storage.leveldb.LDBFactory.factory
import io.horizen.utils.{Pair => JPair, _}
import org.iq80.leveldb.Options

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._


/*
*  @TODO to discuss
*    1. Why we use ByteArrayWrapper instead of Array[Byte]?
*    2. We need iterator over the storage
* */
class VersionedLevelDbStorageAdapter(pathToDB: File, versionsToKeep: Int) extends Storage{

  def this(pathToDB: File) {
    this(pathToDB, 720 * 2 + 1)
  }

  private val dataBase: VersionedLDBKVStore = createDb(pathToDB)
  private val versionsKey: ByteArrayWrapper = new ByteArrayWrapper(dataBase.VersionsKey)

  override def get(key: ByteArrayWrapper): Optional[ByteArrayWrapper] = dataBase.get(key).map(byteArrayToWrapper).asJava

  override def getOrElse(key: ByteArrayWrapper, defaultValue: ByteArrayWrapper): ByteArrayWrapper = dataBase.getOrElse(key, defaultValue)

  override def get(keys: JList[ByteArrayWrapper]): JList[JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]]] = {
    dataBase.get(keys.asScala.map(_.data))
      .map{case (key, value) =>
        new JPair(byteArrayToWrapper(key), value.map(v => byteArrayToWrapper(v)).asJava)}
      .asJava
  }

  override def getAll: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = {
    val excludedKeys: Set[ByteArrayWrapper] = (versionsKey +: dataBase.versions.map(byteArrayToWrapper)).toSet

    dataBase.getAll
        .view
        .map{case (key, value) => (byteArrayToWrapper(key), byteArrayToWrapper(value))}
        .filterNot{case (key, value) => excludedKeys.contains(key)}
        .map{case (key, value) => new JPair(byteArrayToWrapper(key), byteArrayToWrapper(value))}
        .asJava
  }

  override def lastVersionID(): Optional[ByteArrayWrapper] = dataBase.versions.headOption.map(byteArrayToWrapper).asJava

  override def update(version: ByteArrayWrapper, toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]], toRemove: util.List[ByteArrayWrapper]): Unit = {

    val toUpdateAsScala = toUpdate.asScala.toList
    val toRemoveAsScala = toRemove.asScala.toList

    //key for storing version shall not be used as key in any key-value pair in VersionedLDBKVStore
    require(!toUpdateAsScala.exists(pair => pair.getKey == version) && !toRemoveAsScala.contains(version))

    //@TODO added for compatibility with LSMStore, probably interface shall be changed to work with map collection for toUpdate/toremove
    require(toUpdateAsScala.map(_.getKey).toSet.size == toUpdateAsScala.size, "duplicate key in `toUpdate`")
    require(toRemoveAsScala.toSet.size == toRemoveAsScala.size, "duplicate key in `toRemove`")

    require(!isVersionExist(version), "Version is already exist in storage")

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

  override def rollbackVersions(maxNumberOfItems: Int): JList[ByteArrayWrapper] = {
    dataBase.versions.slice(0,maxNumberOfItems).map(byteArrayToWrapper).asJava
  }

  override def close(): Unit = dataBase.close()

  private def createDb(path: File): VersionedLDBKVStore = {
    path.mkdirs()
    val options = new Options()
    options.createIfMissing(true)
    val db = factory.open(path, options)
    new VersionedLDBKVStore(db, versionsToKeep)
  }

  override def isEmpty: Boolean = dataBase.versions.isEmpty
  override def numberOfVersions: Int = dataBase.versions.size

  override def getIterator(): StorageIterator = {
    dataBase.getIterator
  }

}

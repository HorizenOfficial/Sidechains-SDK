package com.horizen.storage.rocksdb

import com.horizen.common.interfaces.DefaultReader
import com.horizen.storage.{StorageNew, StorageVersionedView}
import com.horizen.storageVersioned.{StorageVersioned, TransactionVersioned}
import com.horizen.utils.{Pair => JPair, _}
import scorex.util.ScorexLogging

import java.io.File
import java.util
import java.util.{Map, Optional, List => JList}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionalGeneric


/*
* */
class VersionedRocksDbStorageNewAdapter(pathToDB: File) extends StorageNew with ScorexLogging {
  private val versionsToKeep: Int = 720 * 2 + 1; //How many version could be saved at all, currently hardcoded to two consensus epochs length + 1
  private val dataBase: StorageVersioned = StorageVersioned.open(pathToDB.getAbsolutePath, true, versionsToKeep)

  def createTransaction(versionIdOpt: Optional[String]) : TransactionVersioned = {
    dataBase.createTransaction(versionIdOpt).asScala.getOrElse(throw new Exception("Could not create a transaction"))
  }

  def get(key: Array[Byte]): Array[Byte] = {
    dataBase.get(key).asScala match {
      case None => new Array[Byte](0)
      case Some(arr) => arr
    }
  }

  def getOrElse(key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] = dataBase.getOrElse(key, defaultValue)

  override def get(keyList: util.List[Array[Byte]]): util.List[Array[Byte]] = {
    // TODO: until rocksdb wrapper implements it, use this one by one approach
    //  dataBase.get(keyList)
    keyList.asScala.map(x => get(x)).toList.asJava
  }

  override def getView: StorageVersionedView = new VersionedRocksDbViewAdapter(this, Optional.empty())

  override def getView(version: ByteArrayWrapper): Optional[StorageVersionedView] = {
    isVersionExist(version) match {
      case true => Optional.of(new VersionedRocksDbViewAdapter(this, Optional.of(version)))
      case false => Optional.empty()
    }
  }

  // TODO check if the library has a method we can use without resorting to iterators
  override def getAll: util.Map[Array[Byte], Array[Byte]] = {
    val toWrite = new java.util.HashMap[Array[Byte], Array[Byte]]()
    if (dataBase.isEmpty())
      return toWrite

    // TODO this might throw as well
    val iter = dataBase.asInstanceOf[DefaultReader].getIter()

    try {
      var hasNext = true
      while (hasNext) {
        val next = iter.next()
        if (next.isPresent) {
          val key = next.get().getKey
          val value = next.get().getValue
          toWrite.put(key, value)
        }
        else
          hasNext = false
      }
      toWrite
    } finally {
      iter.close()
    }

  }

  override def lastVersionID(): Optional[ByteArrayWrapper] = {
    dataBase.lastVersion().asScala match {
        case None => Optional.empty()
        case Some(verString) => Optional.of(byteArrayToWrapper(BytesUtils.fromHexString(verString)))
      }
  }

  def lastVersionString(): String = {
    dataBase.lastVersion().asScala match {
      case None => throw new Exception("A versioned db should have at least one version")
      case Some(verString) => verString
    }
  }



  private def isVersionExist(versionForSearch: ByteArrayWrapper): Boolean = {
    val verString = BytesUtils.toHexString(versionForSearch.data())
    val versions : JList[String] = dataBase.rollbackVersions()
    versions.contains(verString)
  }

  override def rollback(versionID: ByteArrayWrapper): Unit = {
    if (isVersionExist(versionID)) {
      dataBase.rollback(BytesUtils.toHexString(versionID))
    }
    else {
      throw new IllegalArgumentException("Rollback to non exist version")
    }
  }

  override def rollbackVersions(): JList[ByteArrayWrapper] = {
    dataBase.rollbackVersions().asScala.map{
      x => byteArrayToWrapper(BytesUtils.fromHexString(x))}.asJava
  }

  override def close(): Unit = dataBase.close()

  override def isEmpty: Boolean = dataBase.rollbackVersions().isEmpty
}

package com.horizen.storage.rocksdb

import com.horizen.common.{ColumnFamily, DBIterator}
import com.horizen.common.interfaces.{DefaultReader, Reader}
import com.horizen.storage.{VersionedStorage, VersionedStoragePartitionView, VersionedStorageView}
import com.horizen.storageVersioned.{StorageVersioned, TransactionVersioned}
import com.horizen.utils.{Pair => JPair, _}
import scorex.util.ScorexLogging

import java.io.File
import java.util
import java.util.{Optional, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters.RichOptionalGeneric


/*
* */
class VersionedRocksDbStorageAdapter(pathToDB: File) extends VersionedStorage with ScorexLogging {
  private val versionsToKeep: Int = 720 * 2 + 1; //How many version could be saved at all, currently hardcoded to two consensus epochs length + 1
  private val dataBase: StorageVersioned = StorageVersioned.open(pathToDB.getAbsolutePath, true, versionsToKeep)

  override def addLogicalPartition(name : String) = {
    // this may throw exceptions
    dataBase.setColumnFamily(name)
  }

  def getDefaultCf(): ColumnFamily =
    dataBase.defaultCf()

  def getLogicalPartition(name: String): Optional[ColumnFamily] = {
    dataBase.getColumnFamily(name)
  }

  def createTransaction(versionIdOpt: Optional[String]) : TransactionVersioned = {
    dataBase.createTransaction(versionIdOpt).asScala.getOrElse(throw new Exception("Could not create a transaction"))
  }

  def get(key: Array[Byte]): Array[Byte] = {
    dataBase.get(key).asScala match {
      case None => new Array[Byte](0)
      case Some(arr) => arr
    }
  }

  override def get(partitionName: String, key: Array[Byte]): Array[Byte] = {
    dataBase.get(getLogicalPartition(partitionName).get(), key).asScala match {
      case None => new Array[Byte](0)
      case Some(arr) => arr
    }
  }

  def getOrElse(key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] = {
    dataBase.getOrElse(key, defaultValue)
  }

  def getOrElse(partitionName: String, key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] = {
    dataBase.getOrElse(getLogicalPartition(partitionName).get(), key, defaultValue)
  }

  override def get(keyList: util.List[Array[Byte]]): util.List[Array[Byte]] = {
    // TODO: until rocksdb wrapper implements it, use this one by one approach
    //  dataBase.get(keyList)
    keyList.asScala.map(x => get(x)).toList.asJava
  }

  override def getView: VersionedStorageView = new VersionedRocksDbViewAdapter(this, Optional.empty())

  override def getView(version: ByteArrayWrapper): Optional[VersionedStorageView] = {
    isVersionExist(version) match {
      case true => Optional.of(new VersionedRocksDbViewAdapter(this, Optional.of(version)))
      case false => Optional.empty()
    }
  }

  def get_all_internal(iter: DBIterator): java.util.ArrayList[JPair[Array[Byte], Array[Byte]]] = {

    val toWrite = new java.util.ArrayList[JPair[Array[Byte], Array[Byte]]]()

    var hasNext = true
    while (hasNext) {
      val next = iter.next()
      if (next.isPresent) {
        val key = next.get().getKey
        val value = next.get().getValue
        toWrite.add(new JPair[Array[Byte], Array[Byte]](key, value))
      }
      else
        hasNext = false
    }
    toWrite
  }

  // TODO check if the library has a method we can use without resorting to iterators
  override def getAll: JList[JPair[Array[Byte], Array[Byte]]] = {

    if (dataBase.isEmpty())
      return new java.util.ArrayList[JPair[Array[Byte], Array[Byte]]]()

    // TODO this might throw as well
    val iter = dataBase.asInstanceOf[DefaultReader].getIter()

    try {
      get_all_internal(iter)
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

  override def get(partitionName: String, keys: JList[Array[Byte]]): JList[Array[Byte]] =
    getPartitionView(partitionName).asScala match {
      case None =>  new java.util.ArrayList[Array[Byte]] ()
      case Some(v) => v.get(keys)
    }

  override def getAll(partitionName: String): JList[JPair[Array[Byte], Array[Byte]]] = {
    val toWrite = new java.util.ArrayList[JPair[Array[Byte], Array[Byte]]]()

    if (getLogicalPartition(partitionName).isEmpty)
      throw new Exception("Nosuch partition")

    val cf = getLogicalPartition(partitionName).get()

    if (dataBase.isEmpty(cf))
      return toWrite

    // TODO this might throw as well
    val iter = dataBase.asInstanceOf[Reader].getIter(cf)

    try {
      get_all_internal(iter)
    } finally {
      iter.close()
    }
  }

  override def getPartitionView(logicalPartitionName: String): Optional[VersionedStoragePartitionView] = {
    getView.getPartitionView(logicalPartitionName)
  }

  override def getPartitionView(logicalPartitionName: String, version: ByteArrayWrapper): Optional[VersionedStoragePartitionView] = {
    getView(version).asScala match {
      case None => Optional.empty()
      case Some(pv) =>
        pv.getPartitionView(logicalPartitionName).asScala match {
          case None => Optional.empty()
          case Some(p) => Optional.of(p)
        }
    }
  }
}

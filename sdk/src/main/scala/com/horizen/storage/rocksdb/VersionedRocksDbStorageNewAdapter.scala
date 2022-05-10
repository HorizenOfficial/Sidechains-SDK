package com.horizen.storage.rocksdb

import com.horizen.common.interfaces.DefaultReader
import com.horizen.storage.StorageNew
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

  def get(key: Array[Byte]): java.util.Optional[Array[Byte]] = dataBase.get(key)

  def getOrElse(key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] = dataBase.getOrElse(key, defaultValue)

  override def get(keySet: util.Set[Array[Byte]]): util.Map[Array[Byte], Optional[Array[Byte]]] = {
    dataBase.get(keySet)
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

  override def update(version: ByteArrayWrapper,
                      toUpdate: util.Map[Array[Byte], Array[Byte]],
                      toRemove: util.Set[Array[Byte]]): Unit = {
    require(!isVersionExist(version), s"Version ${BytesUtils.toHexString(version)} already exists in storage")

    // TODO is this still valid for RocksDb? Probably not
    // key for storing version shall not be used as key in any key-value pair in VersionedLDBKVStore
    // require(!toUpdate.containsKey(version.data()))

    val versionIdOpt: Optional[String] = Optional.empty()
    val transaction: TransactionVersioned = dataBase.createTransaction(versionIdOpt).asScala.getOrElse(throw new Exception("Could not create a transaction"))

    // update and commit may throw exceptions (create does not)
    try {
      transaction.update(toUpdate, toRemove)
      transaction.commit(Optional.of(BytesUtils.toHexString(version)))

    } catch {
      case e: Throwable => log.error(s"Could not update RocksDB with version ${version}", e)
    } finally {
      transaction.close()
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

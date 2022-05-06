package com.horizen.storage.rocksdb

import akka.japi.Option.scala2JavaOption
import com.horizen.common.interfaces.DefaultReader
import com.horizen.storage.Storage
import com.horizen.storageVersioned.{StorageVersioned, TransactionVersioned}
import com.horizen.utils.{Pair => JPair, _}
import scorex.util.ScorexLogging

import java.io.File
import java.util
import java.util.stream.Collectors
import java.util.{Optional, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.OptionConverters.{RichOptionForJava8, RichOptionalGeneric}




/*
* */
class VersionedRocksDbStorageAdapter(pathToDB: File) extends Storage with ScorexLogging {
  private val versionsToKeep: Int = 720 * 2 + 1; //How many version could be saved at all, currently hardcoded to two consensus epochs length + 1
  private val dataBase: StorageVersioned = StorageVersioned.open(pathToDB.getAbsolutePath, true, versionsToKeep)


  override def get(key: ByteArrayWrapper): Optional[ByteArrayWrapper] =
    dataBase.get(key).map(byteArrayToWrapper)

  override def getOrElse(key: ByteArrayWrapper, defaultValue: ByteArrayWrapper): ByteArrayWrapper = dataBase.getOrElse(key, defaultValue)

  override def get(keys: JList[ByteArrayWrapper]): JList[JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]]] = {
    // convert input to a java set
    val keySet = new java.util.HashSet[Array[Byte]]()
    for (x : ByteArrayWrapper <- keys.asScala) {
      val k : Array[Byte] = x.data()
      keySet.add(k)
    }

    // call the method and store the result
    val result : java.util.Map[Array[Byte], Optional[Array[Byte]]] = dataBase.get(keySet)

    // convert the result
    val toWrite = new java.util.ArrayList[JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]]]()
    for (y <- result.entrySet().asScala) {
      toWrite.add(new JPair[ByteArrayWrapper, Optional[ByteArrayWrapper]](byteArrayToWrapper(y.getKey), y.getValue.map(byteArrayToWrapper)))
    }
    toWrite

   }

  // TODO check if the library has a method we can use without resorting to iterators
  override def getAll: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = {

    // TODO this might throw as well
    val iter = dataBase.asInstanceOf[DefaultReader].getIter()

    try {
      val toWrite = new java.util.ArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
      var hasNext = true
      while (hasNext) {
        val next = iter.next()
        if (next.isPresent) {
          val key = byteArrayToWrapper(next.get().getKey)
          val value = byteArrayToWrapper(next.get().getValue)
          toWrite.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](key, value))
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

  override def update(version: ByteArrayWrapper, toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]], toRemove: util.List[ByteArrayWrapper]): Unit = {
    require(!isVersionExist(version), s"Version ${BytesUtils.toHexString(version)} already exists in storage")

    val toInsert = new java.util.HashMap[Array[Byte], Array[Byte]]()
    val toDelete = new java.util.HashSet[Array[Byte]]()
    val verString = BytesUtils.toHexString(version.data())

    for (y <- toUpdate.asScala) {
      // TODO is this still valid for RocksDb?
      // key for storing version shall not be used as key in any key-value pair in VersionedLDBKVStore
      require(y.getKey !=  version)
      // we are inserting into a map, should we throw an except if there are duplicate key?
      toInsert.put(y.getKey.data(), y.getValue.data())
    }

    for (y <- toRemove.asScala) {
      // we are inserting into a set, should we throw an except if there are duplicate key?
      toDelete.add(y.data())
    }



    val versionIdOpt: Optional[String] = Optional.empty()
    val transaction: TransactionVersioned = dataBase.createTransaction(versionIdOpt).asScala.getOrElse(throw new Exception("Could not create a transaction"))

    // update and commit may throw exceptions (create does not)
    try {
      transaction.update(toInsert, toDelete)
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

package com.horizen.storage.rocksdb

import com.horizen.common.ColumnFamily
import com.horizen.storage.{VersionedStoragePartitionView, VersionedStorage, VersionedStorageView}
import com.horizen.storageVersioned.TransactionVersioned
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, Pair, byteArrayToWrapper}
import scorex.util.ScorexLogging

import java.util
import java.util.{List, Optional}
import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsScalaMapConverter, seqAsJavaListConverter}
import scala.compat.java8.OptionConverters.RichOptionalGeneric

class VersionedRocksDbViewAdapter(storage: VersionedRocksDbStorageAdapter, version: Optional[ByteArrayWrapper])
  extends VersionedStorageView with ScorexLogging {

  private val _version : Optional[String] = version.asScala match {
    case None => Optional.empty()
    case Some(ver) => Optional.of(BytesUtils.toHexString(ver.data()))
  }

  private val transaction : TransactionVersioned = {
    storage.createTransaction(_version)
  }

  override def getVersion: Optional[String] = _version

  override def update(toUpdate: util.List[Pair[Array[Byte], Array[Byte]]], toRemove: util.List[Array[Byte]]): Unit = {
    update_internal(storage.getDefaultCf(), toUpdate, toRemove)
  }

  private def update_internal(cf: ColumnFamily, toUpdate: util.List[Pair[Array[Byte], Array[Byte]]], toRemove: util.List[Array[Byte]]): Unit = {

    // TODO: until rocksdb wrapper implements it, use this list->map/set
    val toInsert = new java.util.HashMap[Array[Byte], Array[Byte]]()
    val toDelete = new java.util.HashSet[Array[Byte]]()

    val toUpdateKeysAsScala = toUpdate.asScala.map(x => byteArrayToWrapper(x.getKey))
    val toRemoveKeysAsScala = toRemove.asScala.map(x => byteArrayToWrapper(x))

    // check we have no repetition in both inputs
    require(toUpdateKeysAsScala.toSet.size == toUpdateKeysAsScala.size, "duplicate key in `toUpdate`")
    require(toRemoveKeysAsScala.toSet.size == toRemoveKeysAsScala.size, "duplicate key in `toRemove`")

    for (y <- toUpdate.asScala) {
      toInsert.put(y.getKey, y.getValue)
    }

    for (y <- toRemove.asScala) {
      toDelete.add(y.data())
    }

    try {
      transaction.update(cf, toInsert, toDelete)
    } catch {
      case e: Throwable =>
        log.error(s"Could not update RocksDB view on version ${storage.lastVersionID()}", e)
        // TODO Should we close this transaction? The exception might be caused by a different transactions contending the same key.
        //  we might retry later as soon as the other transaction
        //  has complete the commit
        //  transaction.close()
        throw e
    }
  }

  override def update(partitionName: String, toUpdate: util.List[Pair[Array[Byte], Array[Byte]]], toRemove: util.List[Array[Byte]]): Unit = {
    storage.getLogicalPartition(partitionName).asScala match {
      case None => throw new Exception(s"No such partition: ${partitionName}")
      case Some(cf) => update_internal(cf, toUpdate, toRemove)
    }
  }

  override def commit(version: ByteArrayWrapper): Unit = {
    try {
      transaction.commit(Optional.of(BytesUtils.toHexString(version.data())))
    } catch {
      case e: Throwable =>
        log.error(s"Could not update RocksDB with version ${version}", e)
        throw e
    } finally {
      transaction.close()
    }
  }

  override def get(key: Array[Byte]): Array[Byte] = {
    transaction.get(key).asScala match {
      case None => new Array[Byte](0)
      case Some(arr) => arr
    }
   }

  override def get(partitionName: String, key: Array[Byte]): Array[Byte] = {
    transaction.get(storage.getLogicalPartition(partitionName).get(), key).asScala match {
      case None => new Array[Byte](0)
      case Some(arr) => arr
    }
  }



  override def get(keyList: java.util.List[Array[Byte]]): java.util.List[Array[Byte]] = {
    // TODO: until rocksdb wrapper implements it, use this one by one approach
    //  transaction.get(keyList)
    keyList.asScala.map(x => get(x)).toList.asJava
    /*

    val inputSet = new java.util.HashSet[Array[Byte]]()
    val inputSeq: Seq[Array[Byte]] = keyList.asScala

    for (x <- inputSeq)
      inputSet.add(x)

    val resMap = transaction.get(inputSet)

    val outputSeq = new java.util.ArrayList[Array[Byte]]()

    for (x <- inputSeq) {
      breakable { for (pair <- resMap.asScala) {
        if (byteArrayToWrapper(pair._1) == byteArrayToWrapper(x)) {
          if (pair._2.isPresent)
            outputSeq.add(pair._2.get())
            break
          }
        }
      }
    }
    outputSeq
  */
  }

  override def getOrElse(key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] =
    transaction.getOrElse(key, defaultValue)

  override def getAll: util.List[Pair[Array[Byte], Array[Byte]]] = ???

  override def getOrElse(partitionName: String, key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] = {
    transaction.getOrElse(storage.getLogicalPartition(partitionName).get(), key, defaultValue)
  }

  override def get(partitionName: String, keys: util.List[Array[Byte]]): util.List[Array[Byte]] =
  {

    keys.asScala.map(x => {
      transaction.get(storage.getLogicalPartition(partitionName).get(), x).asScala match {
        case None => new Array[Byte](0)
        case Some(arr) => arr
      }
    }
    ).toList.asJava
  }

  override def getAll(partitionName: String): util.List[Pair[Array[Byte], Array[Byte]]] = ???

  override def getPartitionView(partitionName: String): Optional[VersionedStoragePartitionView] = {
    // TODO should we prevent having more than one view on the same partition? If we must, we should
    // store the 'singleton' obj and return it, or we might raise an exception if someone is doing it
    // After all what is the use of having a view just for read? One could use the storage directly
    storage.getLogicalPartition(partitionName).asScala match {
      case None => Optional.empty()
      case Some(x) => Optional.of(new VersionedRocksDbPartitionViewAdapter(this, partitionName))
    }
  }
}

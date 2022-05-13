package com.horizen.storage.rocksdb

import com.horizen.storage.{VersionedStoragePartitionView, VersionedStorageView}
import com.horizen.utils
import com.horizen.utils.ByteArrayWrapper

import java.util

class VersionedRocksDbPartitionViewAdapter(view: VersionedStorageView, partitionName : String) extends VersionedStoragePartitionView {
  override def update(toUpdate: util.List[utils.Pair[Array[Byte], Array[Byte]]], toRemove: util.List[Array[Byte]]): Unit = {
    view.update(partitionName, toUpdate, toRemove)
  }

  override def commit(version: ByteArrayWrapper): Unit = view.commit(version)

  override def get(key: Array[Byte]): Array[Byte] = view.get(partitionName, key)

  override def getOrElse(key: Array[Byte], defaultValue: Array[Byte]): Array[Byte] = view.getOrElse(partitionName, key, defaultValue)

  override def get(keys: util.List[Array[Byte]]): util.List[Array[Byte]] = view.get(partitionName, keys)

  override def getAll: util.List[utils.Pair[Array[Byte], Array[Byte]]] = view.getAll(partitionName)
}

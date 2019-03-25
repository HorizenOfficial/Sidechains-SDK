package com.horizen.storage

import java.util.{ArrayList => JArrayList, List => JList}
import java.util.Optional
import javafx.util.Pair

import scala.collection.JavaConverters._

import io.iohk.iodb.Store
import com.horizen.utils.ByteArrayWrapper

import scala.collection.mutable.ArrayBuffer

class IODBStoreAdapter (store : Store)
  extends Storage {

  override def get(key: ByteArrayWrapper): Optional[ByteArrayWrapper] = {
    val value = store.get(key)
    if (value.isEmpty)
      Optional.empty()
    else
      Optional.of(new ByteArrayWrapper(value.get))
  }

  override def getOrElse(key: ByteArrayWrapper, defaultValue: ByteArrayWrapper): ByteArrayWrapper = {
    val value = store.get(key)
    if (value.isEmpty)
      defaultValue
    else
      new ByteArrayWrapper(value.get)
  }

  override def get(keys: JList[ByteArrayWrapper]): JList[Pair[ByteArrayWrapper, Optional[ByteArrayWrapper]]] = {
    val keysList = new ArrayBuffer[ByteArrayWrapper]();
    val valList = store.get(keys.asScala)
    val values = new JArrayList[Pair[ByteArrayWrapper,Optional[ByteArrayWrapper]]]()

    for (v <- valList)
      if (v._2.isEmpty)
        values.add(new Pair[ByteArrayWrapper,Optional[ByteArrayWrapper]](new ByteArrayWrapper(v._1),
          Optional.of(new ByteArrayWrapper(v._2.get))))
      else
        values.add(new Pair[ByteArrayWrapper,Optional[ByteArrayWrapper]](new ByteArrayWrapper(v._1),
          Optional.empty()))

    values
  }

  override def getAll: JList[Pair[ByteArrayWrapper, ByteArrayWrapper]] = {
    val values = new JArrayList[Pair[ByteArrayWrapper,ByteArrayWrapper]]()

    for ( i <- store.getAll())
      values.add(new Pair[ByteArrayWrapper,ByteArrayWrapper](new ByteArrayWrapper(i._1),
        new ByteArrayWrapper(i._2)))

    values
  }

  override def lastVersionID(): Optional[ByteArrayWrapper] = {
    val value = store.lastVersionID
    if (value.isEmpty)
      Optional.empty()
    else
      Optional.of(new ByteArrayWrapper(value.get))
  }

  override def update(version: ByteArrayWrapper, toRemove: JList[ByteArrayWrapper],
                      toUpdate: JList[Pair[ByteArrayWrapper, ByteArrayWrapper]]): Unit = {

    val listToUpdate = new ArrayBuffer[Tuple2[ByteArrayWrapper,ByteArrayWrapper]]()

    for (r <- toUpdate.asScala) {
      listToUpdate.append(new Tuple2[ByteArrayWrapper, ByteArrayWrapper](r.getKey, r.getValue))
    }

    store.update(version, toRemove.asScala, listToUpdate)
  }

  override def rollback(version : ByteArrayWrapper): Unit = {
    store.rollback(version)
  }

  override def rollbackVersions(): JList[ByteArrayWrapper] = {
    val versions = store.rollbackVersions()
    val value = new JArrayList[ByteArrayWrapper]();
    for (v <- versions)
      value.add(new ByteArrayWrapper(v))

    value
  }

  override def close(): Unit = {
    store.close()
  }
}

package com.horizen.fixtures

import com.horizen.storage.StorageNew
import com.horizen.storage.rocksdb.{VersionedRocksDbStorageAdapter, VersionedRocksDbStorageNewAdapter}
import com.horizen.utils.{ByteArrayWrapper, Pair, byteArrayToWrapper}

import java.util.{ArrayList => JArrayList, List => JList}
import java.io.File
import java.{lang, util}
import scala.collection.JavaConverters.{asScalaBufferConverter, asScalaSetConverter}
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait StoreNewFixture {

  val keySize = 32
  val valueSize = 256
  val versionSize = 32
  val storages: ListBuffer[StorageNew] = new ListBuffer()
  val tempFiles: ListBuffer[File] = new ListBuffer()

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      storages.foreach(_.close())
      tempFiles.flatMap(_.listFiles()).filter(_.exists()).foreach(_.delete())
    }
  })

  def tempDir(): File = {
    val dir = new File(System.getProperty("java.io.tmpdir") + File.separator + "sidechain_storage" + Math.random())
    dir.mkdirs()
    dir
  }

  def tempFile(): File = {
    val ret = new File(System.getProperty("java.io.tmpdir") + File.separator + "leveldb" + Math.random())
    tempFiles.append(ret)
    ret.deleteOnExit()
    ret
  }

  def deleteRecur(dir: File): Unit = {
    if (dir == null) return
    val files = dir.listFiles()
    if (files != null)
      files.foreach(deleteRecur)
    dir.delete()
  }

  def getStorage(): VersionedRocksDbStorageNewAdapter = {
    getStorage(tempFile())
  }

  def getStorage(pathToDB: File): VersionedRocksDbStorageNewAdapter = {
    val storage = new VersionedRocksDbStorageNewAdapter(pathToDB)
    storages.append(storage)
    storage
  }

  def getValue : Array[Byte] = {
    val value = new Array[Byte](valueSize)
    Random.nextBytes(value)
    value
  }

  def getKeyValue : Pair[Array[Byte], Array[Byte]] = {
    val key = new Array[Byte](keySize)

    Random.nextBytes(key)

    new Pair(key, getValue)
  }

  def getKeyValueList (count : Int) : JList[Pair[Array[Byte], Array[Byte]]] = {
    val list = new JArrayList[Pair[Array[Byte], Array[Byte]]]()

    for (i <- 1 to count)
      list.add(getKeyValue)

    list
  }

  def getValueList (count : Int) : JList[Array[Byte]] = {
    val list = new JArrayList[Array[Byte]]()

    for (i <- 1 to count)
      list.add(getValue)
    list
  }

  def getVersion : ByteArrayWrapper = {
    val version = new Array[Byte](versionSize)

    Random.nextBytes(version)

    new ByteArrayWrapper(version)
  }

  def listContainment(big : java.util.List[Pair[Array[Byte], Array[Byte]]], small : java.util.List[Pair[Array[Byte], Array[Byte]]]) : Boolean = {
    val bigAsScala = big.asScala.map( x => (byteArrayToWrapper(x.getKey), byteArrayToWrapper(x.getValue)))
    val smallAsScala = small.asScala.map( x => (byteArrayToWrapper(x.getKey), byteArrayToWrapper(x.getValue)))
    smallAsScala.forall(bigAsScala.contains)
  }

  def compareValues(from: Array[Byte], to: Array[Byte]) : Boolean = {
    new ByteArrayWrapper(from) == new ByteArrayWrapper(to)
  }

}

class StoreNewFixtureClass extends StoreNewFixture

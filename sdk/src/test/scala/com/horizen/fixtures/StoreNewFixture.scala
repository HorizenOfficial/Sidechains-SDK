package com.horizen.fixtures

import com.horizen.storage.StorageNew
import com.horizen.storage.rocksdb.{VersionedRocksDbStorageAdapter, VersionedRocksDbStorageNewAdapter}
import com.horizen.utils.{ByteArrayWrapper, Pair}

import java.io.File
import java.{lang, util}
import scala.collection.JavaConverters.asScalaSetConverter
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

  def getKeyValue : (Array[Byte], Array[Byte]) = {
    val key = new Array[Byte](keySize)

    Random.nextBytes(key)

    (key, getValue)
  }

  def getKeyValueList (count : Int) : java.util.Map[Array[Byte], Array[Byte]] = {
    val dum = new util.HashMap[Array[Byte], Array[Byte]]()

    for (i <- 1 to count) {
      val entry = getKeyValue
      dum.put(entry._1, entry._2)
    }

    dum
  }

  def getValueList (count : Int) : java.util.Set[Array[Byte]] = {
    val dum = new java.util.HashSet[Array[Byte]]()

    for (i <- 1 to count)
      dum.add(getValue)
    dum
  }

  def getVersion : ByteArrayWrapper = {
    val version = new Array[Byte](versionSize)

    Random.nextBytes(version)

    new ByteArrayWrapper(version)
  }

  def compareMaps(from : java.util.Map[Array[Byte], Array[Byte]], to : java.util.Map[Array[Byte], Array[Byte]]) : Boolean = {
    val bawU2 = new util.ArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]
    for (entry <- from.entrySet().asScala) {
      bawU2.add(new Pair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(entry.getKey), new ByteArrayWrapper(entry.getValue)))
    }

    val bawS = new util.ArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]
    for (entry <- to.entrySet().asScala) {
      bawS.add(new Pair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(entry.getKey), new ByteArrayWrapper(entry.getValue)))
    }
    bawS.containsAll(bawU2)
  }

  def compareValues(from: Array[Byte], to: Array[Byte]) : Boolean = {
    new ByteArrayWrapper(from) == new ByteArrayWrapper(to)
  }

  def mapContainsKey(inMap: java.util.Map[Array[Byte], Array[Byte]], inKey : Array[Byte]) : Boolean = {
    for (entry <- inMap.keySet().asScala) {
      if (new ByteArrayWrapper(entry) == new ByteArrayWrapper(inKey)) {
        return true
      }
    }
    false
  }
}

class StoreNewFixtureClass extends StoreNewFixture

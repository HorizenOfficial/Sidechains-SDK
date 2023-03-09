package io.horizen.fixtures

import io.horizen.storage.Storage
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.utils.{ByteArrayWrapper, Pair}

import java.io.File
import java.util.{ArrayList => JArrayList, List => JList}
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait StoreFixture {

  val keySize = 32
  val valueSize = 256
  val versionSize = 32
  val storages: ListBuffer[Storage] = new ListBuffer()
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

  def getStorage(): VersionedLevelDbStorageAdapter = {
    getStorage(tempFile())
  }

  def getStorage(pathToDB: File): VersionedLevelDbStorageAdapter = {
    val storage = new VersionedLevelDbStorageAdapter(pathToDB)
    storages.append(storage)
    storage
  }

  def getValue : ByteArrayWrapper = {
    val value = new Array[Byte](valueSize)
    Random.nextBytes(value)
    new ByteArrayWrapper(value)
  }

  def getKeyValue : Pair[ByteArrayWrapper,ByteArrayWrapper] = {
    val key = new Array[Byte](keySize)

    Random.nextBytes(key)

    new Pair(new ByteArrayWrapper(key), getValue)
  }

  def getKeyValueList (count : Int) : JList[Pair[ByteArrayWrapper,ByteArrayWrapper]] = {
    val list = new JArrayList[Pair[ByteArrayWrapper,ByteArrayWrapper]]()
    var key = new Array[Byte](keySize)
    var value = new Array[Byte](valueSize)

    for (i <- 1 to count)
      list.add(getKeyValue)

    list
  }

  def getVersion : ByteArrayWrapper = {
    val version = new Array[Byte](versionSize)

    Random.nextBytes(version)

    new ByteArrayWrapper(version)
  }

}

class StoreFixtureClass extends StoreFixture

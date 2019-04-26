package com.horizen.fixtures

import java.io.File
import javafx.util.Pair
import java.util.{List => JList, ArrayList => JArrayList}

import io.iohk.iodb.LSMStore
import io.iohk.iodb.Store

import com.horizen.utils.ByteArrayWrapper

import scala.util.Random
import scala.collection.mutable.ListBuffer

trait IODBStoreFixture {

  val keySize = 32
  val valueSize = 256
  val versionSize = 32
  val stores : ListBuffer[Tuple2[File, LSMStore]] = new ListBuffer()

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      for (s <- stores) {
        s._2.close()
        for (f <- s._1.listFiles())
          f.delete()
        s._1.delete()
      }
    }
  })

  def tempDir(): File = {
    val dir = new File(System.getProperty("java.io.tmpdir") + File.separator + "iodb" + Math.random())
    dir.mkdirs()
    dir
  }

  def tempFile(): File = {
    val ret = new File(System.getProperty("java.io.tmpdir") + File.separator + "iodb" + Math.random())
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

  def getStore() : Store = {
    val dir = tempDir()
    val store = new LSMStore(dir, keySize)
    stores.append(Tuple2(dir, store))
    store
  }

  def getStoreWithPath() : (Store, File) = {
    val dir = tempDir()
    val store = new LSMStore(dir, keySize)
    stores.append(Tuple2(dir, store))
    (store, dir)
  }

  def getStore (dir: File) : Store = {
    new LSMStore(dir, keySize)
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

class IODBStoreFixtureClass extends IODBStoreFixture

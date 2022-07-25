package com.horizen.storage.leveldb

import java.util

import com.google.common.primitives.Longs
import com.horizen.fixtures.StoreFixture
import com.horizen.utils
import com.horizen.utils.ByteArrayWrapper
import org.junit.{Rule, Test}
import org.junit.rules.TemporaryFolder
import org.scalatestplus.junit.JUnitSuite
import org.junit.Assert._

import scala.util.Random

class VersionedLevelDbStorageAdapterTest extends JUnitSuite with StoreFixture {

  val _temporaryFolder = new TemporaryFolder()
  var random = new Random()
  @Rule
  def temporaryFolder = _temporaryFolder


  @Test
  def testCleanupStorage(): Unit = {
    val stateStorageFile = temporaryFolder.newFolder("tempTestStorage")
    val stateStorage = new VersionedLevelDbStorageAdapter(stateStorageFile)
    stateStorage.update(getVersion, getKeyValueList(2), new util.ArrayList[ByteArrayWrapper]())
    stateStorage.update(getVersion, getKeyValueList(3), new util.ArrayList[ByteArrayWrapper]())
    assertEquals(5, stateStorage.getAll.size())
    stateStorage.cleanup()
    assertEquals(0, stateStorage.getAll.size())
    stateStorage.update(getVersion, getKeyValueList(3), new util.ArrayList[ByteArrayWrapper]())
    assertEquals(3, stateStorage.getAll.size())
  }
}


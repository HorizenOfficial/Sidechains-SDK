package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.box.{ForgerBox, ForgerBoxSerializer}
import com.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.utils.{ByteArrayWrapper, Pair}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.crypto.hash.Blake2b256

import java.util.{ArrayList => JArrayList, Optional => JOptional}
import scala.collection.mutable.ListBuffer
import scala.util.Try
import scala.collection.JavaConverters._

class SidechainStateForgerBoxStorageTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypes
{
  val mockedPhysicalStorage: Storage = mock[VersionedLevelDbStorageAdapter]

  val boxList = new ListBuffer[ForgerBox]()
  val storedBoxList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  @Before
  def setUp(): Unit = {
    boxList ++= getForgerBoxList(5).asScala.toList


    for (b <- boxList) {
      storedBoxList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(b.id()))
        val value = new ByteArrayWrapper(ForgerBoxSerializer.getSerializer.toBytes(b))
        new Pair(key, value)
      })
    }

    Mockito.when(mockedPhysicalStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedBoxList.find(_.getKey.equals(answer.getArgument(0))) match {
          case Some(pair) => JOptional.of(pair.getValue)
          case None => JOptional.empty()
        }
      })
  }

  @Test
  def testUpdate(): Unit = {
    val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(mockedPhysicalStorage)
    var tryRes: Try[SidechainStateForgerBoxStorage] = null
    val expectedException = new IllegalArgumentException("on update exception")

    // Test1: get one item
    assertEquals("Storage must return existing Box.", boxList(3), sidechainStateForgerBoxStorage.getForgerBox(boxList(3).id()).get)

    // Test 2: try get non-existing item
    assertEquals("Storage must NOT contain requested Box.", None, sidechainStateForgerBoxStorage.getForgerBox("non-existing id".getBytes()))

    // Data for Test 3:
    val version = getVersion
    val toUpdate = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    toUpdate.add(storedBoxList.head)
    // consensus epoch
    val toRemove = java.util.Arrays.asList(storedBoxList(2).getKey)

    Mockito.when(mockedPhysicalStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualVersion = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]
      assertEquals("SidechainStateForgerBoxStorage.update(...) actual Version is wrong.", version, actualVersion)
      assertEquals("SidechainStateForgerBoxStorage.update(...) actual toUpdate list is wrong.", toUpdate, actualToUpdate)
      assertEquals("SidechainStateForgerBoxStorage.update(...) actual toRemove list is wrong.", toRemove, actualToRemove)
    })
      // For Test 4:
      .thenAnswer(answer => throw expectedException)


    // Test 3: test successful update
    tryRes = sidechainStateForgerBoxStorage.update(version, Seq(boxList.head), Set(new ByteArrayWrapper(boxList(2).id())))
    assertTrue("SidechainStateForgerBoxStorage successful update expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)


    // Test 4: test failed update, when Storage throws an exception
    val forgerBox = getForgerBox
    tryRes = sidechainStateForgerBoxStorage.update(version, Seq(forgerBox), Set(new ByteArrayWrapper(boxList(3).id())))
    assertTrue("SidechainStateForgerBoxStorage failure expected during update.", tryRes.isFailure)
    assertEquals("SidechainStateForgerBoxStorage different exception expected during update.", expectedException, tryRes.failed.get)
    assertTrue("Storage should NOT contain Box that was tried to update.", sidechainStateForgerBoxStorage.getForgerBox(forgerBox.id()).isEmpty)
    assertTrue("Storage should contain Box that was tried to remove.", sidechainStateForgerBoxStorage.getForgerBox(boxList(3).id()).isDefined)
    assertEquals("Storage should return existing Box.", boxList(3), sidechainStateForgerBoxStorage.getForgerBox(boxList(3).id()).get)
  }

  @Test
  def testExceptions() : Unit = {
    var exceptionThrown = false

    try {
      val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(null)
    } catch {
      case _: IllegalArgumentException => exceptionThrown = true
    }

    assertTrue("SidechainStateStorage constructor. Exception must be thrown if storage is not specified.",
      exceptionThrown)


    val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(mockedPhysicalStorage)
    assertTrue("SidechainStorage.rollback. Method must return Failure if NULL version specified.",
      sidechainStateForgerBoxStorage.rollback(null).isFailure)
  }
}


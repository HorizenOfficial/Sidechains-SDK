package com.horizen.storage

import com.horizen.{SidechainTypes, WalletBox, WalletBoxSerializer}
import com.horizen.box._
import com.horizen.companion._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.utils.ByteArrayWrapper
import com.horizen.utils.Pair

import java.util.{HashMap => JHashMap, List => JList}
import java.lang.{Byte => JByte}
import org.junit.Assert._

import scala.collection.mutable.ListBuffer
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.collection.JavaConverters._
import org.mockito._
import scorex.crypto.hash.Blake2b256

import scala.util.Try


class SidechainWalletBoxStorageTest
  extends JUnitSuite
  with BoxFixture
  with StoreFixture
  with MockitoSugar
  with SidechainTypes
{

  var mockedStorage: Storage = mock[VersionedLevelDbStorageAdapter]
  var boxList = new ListBuffer[WalletBox]()
  var storedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  var customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)
  val sidechainBoxesCompanionCore = SidechainBoxesCompanion(new JHashMap())

  @Before
  def setUp() : Unit = {
    mockedStorage= mock[VersionedLevelDbStorageAdapter]
    boxList = new ListBuffer[WalletBox]()
    storedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

    boxList ++= getWalletBoxList(classOf[ZenBox], 5).asScala ++ getWalletBoxList(classOf[CustomBox], 5).asScala

    for (b <- boxList) {
      storedList.append({
        val wbs = new WalletBoxSerializer(sidechainBoxesCompanion)
        val key = new ByteArrayWrapper(Blake2b256.hash(b.box.id()))
        val value = new ByteArrayWrapper(wbs.toBytes(b))
        new Pair(key,value)
      })
    }

    Mockito.when(mockedStorage.getAll).thenReturn(storedList.asJava)

    Mockito.when(mockedStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedList.filter(p => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

  }

  @Test
  def testGet() : Unit = {
    val walletBoxStorage = new SidechainWalletBoxStorage(mockedStorage, sidechainBoxesCompanion)

    // Test1: get one item
    assertEquals("Storage must return existing WalletBox.", boxList(3), walletBoxStorage.get(boxList(3).box.id()).get)


    // Test2: get multiple items
    val subList = boxList.slice(0, 2)
    assertTrue("Storage must contain specified WalletBoxes.", walletBoxStorage.get(subList.map(_.box.id()).toList).asJava.containsAll(subList.asJava))


    // Test3: get all items
    assertTrue("Storage must contain all WalletBoxes.", walletBoxStorage.getAll.asJava.containsAll(boxList.asJava))


    // Test 4: try get non-existing item
    assertEquals("Storage should NOT contain requested WalletBox.", None, walletBoxStorage.get("non-existing id".getBytes()))


    // Test 5: get multiple items, not all of them exist
    assertEquals("Storage should contain NOT ALL requested WalletBoxes.", List(boxList.head),
      walletBoxStorage.get(List(boxList.head.box.id(), "non-existing id".getBytes())))


    // Test 6: get by type for existing type
    assertEquals("Storage should contain WalletBoxes of specified type.", boxList.filter(wb => wb.box.isInstanceOf[ZenBox]),
      walletBoxStorage.getByType(classOf[ZenBox]))


    // Test 7: get by type for non-existing type
    assertEquals("Storage should NOT contain WalletBoxes of specified type.", List(),
      walletBoxStorage.getByType(classOf[CustomBoxChild]))
  }


  @Test
  def testUpdate(): Unit = {
    val walletBoxStorage = new SidechainWalletBoxStorage(mockedStorage, sidechainBoxesCompanion)
    var tryRes: Try[SidechainWalletBoxStorage] = null
    val expectedException = new IllegalArgumentException("on update exception")

    val version = getVersion
    val toUpdate = java.util.Arrays.asList(storedList.head)
    val toRemove = java.util.Arrays.asList(storedList(2).getKey)

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
        val actualVersion = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
        val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]
        assertEquals("WalletBoxStorage.update(...) actual Version is wrong.", version, actualVersion)
        assertEquals("WalletBoxStorage.update(...) actual toUpdate list is wrong.", toUpdate, actualToUpdate)
        assertEquals("WalletBoxStorage.update(...) actual toRemove list is wrong.", toRemove, actualToRemove)
      })
      // For Test 2:
      .thenAnswer(answer => throw expectedException)


    // Test 1: test successful update
    tryRes = walletBoxStorage.update(version, List(boxList.head), List(boxList(2).box.id()))
    assertTrue("WalletBoxStorage successful update expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)


    // Test 2: test failed update, when Storage throws an exception
    val walletBox = getWalletBox(classOf[ZenBox])
    tryRes = walletBoxStorage.update(version, List(walletBox), List(boxList(3).box.id()))
    assertTrue("WalletBoxStorage failure expected during update.", tryRes.isFailure)
    assertEquals("WalletBoxStorage different exception expected during update.", expectedException, tryRes.failed.get)
    assertTrue("Storage should NOT contain WalletBox that was tried to update.", walletBoxStorage.get(walletBox.box.id()).isEmpty)
    assertTrue("Storage should contain WalletBox that was tried to remove.", walletBoxStorage.get(boxList(3).box.id()).isDefined)
    assertEquals("Storage should return existing WalletBox.", boxList(3), walletBoxStorage.get(boxList(3).box.id()).get)
   }

}

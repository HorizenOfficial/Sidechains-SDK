package com.horizen.integration.storage

import com.horizen.WalletBox
import com.horizen.box._
import com.horizen.companion._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.proposition._
import com.horizen.storage.{IODBStoreAdapter, SidechainWalletBoxStorage}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.collection.JavaConverters._
import scala.collection.mutable.Map

class SidechainWalletBoxStorageTest
  extends JUnitSuite
    with BoxFixture
    with IODBStoreFixture
{

  val customBoxesSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(CustomBox.BOX_TYPE_ID -> CustomBoxSerializer.getSerializer)
  val sidechainBoxesCompanion = new SidechainBoxesCompanion(customBoxesSerializers)
  val sidechainBoxesCompanionCore = new SidechainBoxesCompanion(Map())

  @Test
  def mainWorkflow() : Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    val wbList1 = getWalletBoxList(classOf[RegularBox], 3).asScala.toList
    val wbList2 = getWalletBoxList(classOf[CertifierRightBox], 3).asScala.toList
    val wbList3 = getWalletBoxList(classOf[CustomBox], 3).asScala.toList
    val version1 = getVersion
    val version2 = getVersion

    // TEST SCENARIO 1: Test add operation to empty storage
    assertTrue("Add operation must be succeessful.",
      sidechainWalletBoxStorage.update(version1, wbList1 ++ wbList2 ++ wbList3, List[Array[Byte]]()).isSuccess)

    // Test storage content
    assertEquals("Storage must have specified version.", version1, sidechainWalletBoxStorage.lastVersionId.get())
    var wbl1 = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 9 items.", 9, wbl1.size)
    for(wb <- wbList1 ++ wbList2 ++ wbList3)
      assertTrue("Storage must contain all specified items.", wbl1.contains(wb))

    wbl1 = sidechainWalletBoxStorage.get(wbList1.map(_.box.id()))
    assertEquals("Storage must contain 3 RegularBoxes.", 3, wbl1.size)
    for(wb <- wbList1)
      assertTrue("Storage must contain all RegularBoxes.", wbl1.contains(wb))

    assertEquals("Storage must contain specified CustomBox.", wbList3.head,
      sidechainWalletBoxStorage.get(wbList3.head.box.id()).get)

    wbl1 = sidechainWalletBoxStorage.getByType(classOf[CertifierRightBox])
    assertEquals("Storage must contain 3 CertifierRightBoxes.", 3, wbl1.size)
    for(wb <- wbList2)
      assertTrue("Storage must contain all CertifierRightBoxes.", wbl1.contains(wb))

    assertEquals("Balances for CertifierRightBox must be same.", wbList2.map(_.box.value()).sum,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CertifierRightBox]))

    // TEST SCENARIO 2: Test update/remove of WalletBoxes on non-empty storage
    assertTrue("Remove operation must be successful.",
      sidechainWalletBoxStorage.update(version2, List[WalletBox](), wbList2.map(_.box.id())).isSuccess)

    assertEquals("Storage must have specified version.", version2, sidechainWalletBoxStorage.lastVersionId.get())
    wbl1 = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 6 items.", 6, wbl1.size)
    for(wb <- wbList1 ++ wbList3)
      assertTrue("Storage must contain all specified items (except removed).", wbl1.contains(wb))

    wbl1 = sidechainWalletBoxStorage.getByType(classOf[CertifierRightBox])
    assertEquals("Storage must not contain CertifierRightBoxes.", 0, wbl1.size)

    assertEquals("Balances for CertifierRightBox must be 0.", 0,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CertifierRightBox]))

    var rv = sidechainWalletBoxStorage.rollbackVersions
    assertEquals("Versions count in storage must be 2.", 2, rv.size)
    assertTrue("Storage must contain specified version.", rv.contains(version1))
    assertTrue("Storage must contain specified version.", rv.contains(version2))

    // TEST SCENARIO 3: Test rollback functionality
    assertTrue("Rollback operation must be successful.", sidechainWalletBoxStorage.rollback(version1).isSuccess)

    assertEquals("Storage must have specified version.", version1, sidechainWalletBoxStorage.lastVersionId.get())
    var wbl2 : List[WalletBox] = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 9 items.", 9, wbl2.size)
    for(wb <- wbList1 ++ wbList2 ++ wbList3)
      assertTrue("Storage must contain all specified items.", wbl2.contains(wb))

    assertEquals("Balances for CertifierRightBox must be same.", wbList2.map(_.box.value()).sum,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CertifierRightBox]))
  }

  @Test
  def balances(): Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    // Test 1: Test balance for Box Type which is NOT present yet in the storage.
    assertEquals("Balance of RegularBoxes should be 0.", 0, sidechainWalletBoxStorage.getBoxesBalance(classOf[RegularBox]))


    // Test 2: Test balance after item of Box Class was added.
    val walletBox1 = getWalletBox(classOf[RegularBox])
    sidechainWalletBoxStorage.update(getVersion, List(walletBox1), List[Array[Byte]]())
    assertEquals("Balance of RegularBoxes should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[RegularBox]))


    // Test 3: Test balance after item of Box Class was removed.
    sidechainWalletBoxStorage.update(getVersion, List[WalletBox](), List(walletBox1.box.id()))
    assertEquals("Balance of RegularBoxes should be 0.", 0, sidechainWalletBoxStorage.getBoxesBalance(classOf[RegularBox]))
  }

  @Test
  def onUpdateExceptionResistance(): Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)
    val walletBox1 = getWalletBox(classOf[RegularBox])
    sidechainWalletBoxStorage.update(getVersion, List(walletBox1), List[Array[Byte]]())

    var exceptionOccurred = false


    // Test 1: try to add duplicate items.
    val walletBox2 = getWalletBox(classOf[RegularBox])
    exceptionOccurred = sidechainWalletBoxStorage.update(getVersion, List(walletBox2, walletBox2), List[Array[Byte]]()).isFailure

    assertTrue("Exception expected on duplicate item insertions.", exceptionOccurred)
    assertEquals("Balance of RegularBoxes should NOT change, so should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[RegularBox]))


    // Test 2: try to remove duplicate items.
    exceptionOccurred = sidechainWalletBoxStorage.update(getVersion, List[WalletBox](), List(walletBox1.box.id(), walletBox1.box.id())).isFailure

    assertTrue("Exception expected on duplicate item removals.", exceptionOccurred)
    assertEquals("Balance of RegularBoxes should NOT change, so should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[RegularBox]))


    // Test 3: try to remove non-existent item.
    exceptionOccurred = sidechainWalletBoxStorage.update(getVersion, List[WalletBox](), List(walletBox2.box.id())).isFailure

    assertFalse("Exception NOT expected on non-existent item removals.", exceptionOccurred)
    assertEquals("Balance of RegularBoxes should NOT change, so should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[RegularBox]))
  }

}

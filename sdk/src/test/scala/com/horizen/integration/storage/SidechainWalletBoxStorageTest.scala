package com.horizen.integration.storage

import com.horizen.{SidechainTypes, WalletBox}
import com.horizen.box._
import com.horizen.companion._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.storage.SidechainWalletBoxStorage
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.collection.JavaConverters._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

class SidechainWalletBoxStorageTest
  extends JUnitSuite
  with BoxFixture
  with StoreFixture
  with SidechainTypes
{

  var customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)
  val sidechainBoxesCompanionCore = SidechainBoxesCompanion(new JHashMap())

  @Test
  def mainWorkflow() : Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(getStorage(), sidechainBoxesCompanion)

    val zenWbList = getWalletBoxList(classOf[ZenBox], 3).asScala.toList
    val customWbList = getWalletBoxList(classOf[CustomBox], 3).asScala.toList
    val version1 = getVersion
    val version2 = getVersion

    // TEST SCENARIO 1: Test add operation to empty storage
    assertTrue("Add operation must be succeessful.",
      sidechainWalletBoxStorage.update(version1, zenWbList ++ customWbList, List[Array[Byte]]()).isSuccess)

    // Test storage content
    assertEquals("Storage must have specified version.", version1, sidechainWalletBoxStorage.lastVersionId.get())
    var wbl1 = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 6 items.", 6, wbl1.size)
    for(wb <- zenWbList ++ customWbList)
      assertTrue("Storage must contain all specified items.", wbl1.contains(wb))

    wbl1 = sidechainWalletBoxStorage.get(zenWbList.map(_.box.id()))
    assertEquals("Storage must contain 3 ZenBoxes.", 3, wbl1.size)
    for(wb <- zenWbList)
      assertTrue("Storage must contain all ZenBoxes.", wbl1.contains(wb))

    assertEquals("Storage must contain specified CustomBox.", customWbList.head,
      sidechainWalletBoxStorage.get(customWbList.head.box.id()).get)

    // TEST SCENARIO 2: Test update/remove of WalletBoxes on non-empty storage
    assertTrue("Remove operation must be successful.",
      sidechainWalletBoxStorage.update(version2, List[WalletBox](), customWbList.map(_.box.id())).isSuccess)

    assertEquals("Storage must have specified version.", version2, sidechainWalletBoxStorage.lastVersionId.get())
    wbl1 = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 3 items.", 3, wbl1.size)
    for(wb <- zenWbList)
      assertTrue("Storage must contain all specified items (except removed).", wbl1.contains(wb))

    wbl1 = sidechainWalletBoxStorage.getByType(classOf[CustomBox])
    assertEquals("Storage must not contain CustomBox.", 0, wbl1.size)

    assertEquals("Balances for CustomBox must be 0.", 0,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CustomBox]))

    var rv = sidechainWalletBoxStorage.rollbackVersions
    assertEquals("Versions count in storage must be 2.", 2, rv.size)
    assertTrue("Storage must contain specified version.", rv.contains(version1))
    assertTrue("Storage must contain specified version.", rv.contains(version2))

    // TEST SCENARIO 3: Test rollback functionality
    assertTrue("Rollback operation must be successful.", sidechainWalletBoxStorage.rollback(version1).isSuccess)

    assertEquals("Storage must have specified version.", version1, sidechainWalletBoxStorage.lastVersionId.get())
    var wbl2 : List[WalletBox] = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 6 items.", 6, wbl2.size)
    for(wb <- zenWbList ++ customWbList)
      assertTrue("Storage must contain all specified items.", wbl2.contains(wb))

    assertEquals("Balances for CustomBox must be same.", customWbList.map(_.box.value()).sum,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CustomBox]))
  }

  @Test
  def balances(): Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(getStorage(), sidechainBoxesCompanion)

    // Test 1: Test balance for Box Type which is NOT present yet in the storage.
    assertEquals("Balance of ZenBoxes should be 0.", 0, sidechainWalletBoxStorage.getBoxesBalance(classOf[ZenBox]))


    // Test 2: Test balance after item of Box Class was added.
    val walletBox1 = getWalletBox(classOf[ZenBox])
    sidechainWalletBoxStorage.update(getVersion, List(walletBox1), List[Array[Byte]]())
    assertEquals("Balance of ZenBoxes should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[ZenBox]))


    // Test 3: Test balance after item of Box Class was removed.
    sidechainWalletBoxStorage.update(getVersion, List[WalletBox](), List(walletBox1.box.id()))
    assertEquals("Balance of ZenBoxes should be 0.", 0, sidechainWalletBoxStorage.getBoxesBalance(classOf[ZenBox]))
  }

  @Test
  def onUpdateExceptionResistance(): Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(getStorage(), sidechainBoxesCompanion)
    val walletBox1 = getWalletBox(classOf[ZenBox])
    sidechainWalletBoxStorage.update(getVersion, List(walletBox1), List[Array[Byte]]())

    var exceptionOccurred = false


    // Test 1: try to add duplicate items.
    val walletBox2 = getWalletBox(classOf[ZenBox])
    exceptionOccurred = sidechainWalletBoxStorage.update(getVersion, List(walletBox2, walletBox2), List[Array[Byte]]()).isFailure

    assertTrue("Exception expected on duplicate item insertions.", exceptionOccurred)
    assertEquals("Balance of ZenBoxes should NOT change, so should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[ZenBox]))


    // Test 2: try to remove duplicate items.
    exceptionOccurred = sidechainWalletBoxStorage.update(getVersion, List[WalletBox](), List(walletBox1.box.id(), walletBox1.box.id())).isFailure

    assertTrue("Exception expected on duplicate item removals.", exceptionOccurred)
    assertEquals("Balance of ZenBoxes should NOT change, so should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[ZenBox]))


    // Test 3: try to remove non-existent item.
    exceptionOccurred = sidechainWalletBoxStorage.update(getVersion, List[WalletBox](), List(walletBox2.box.id())).isFailure

    assertFalse("Exception NOT expected on non-existent item removals.", exceptionOccurred)
    assertEquals("Balance of ZenBoxes should NOT change, so should be %d.".format(walletBox1.box.value()), walletBox1.box.value(), sidechainWalletBoxStorage.getBoxesBalance(classOf[ZenBox]))
  }

}

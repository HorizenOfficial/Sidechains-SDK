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
  def test() : Unit = {
    val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    val wbList1 = getWalletBoxList(classOf[RegularBox], 3).asScala.toList
    val wbList2 = getWalletBoxList(classOf[CertifierRightBox], 3).asScala.toList
    val wbList3 = getWalletBoxList(classOf[CustomBox], 3).asScala.toList
    val version1 = getVersion
    val version2 = getVersion
    val version3 = getVersion

    //SCENARIO - Test add operation to empty storage
    assertTrue("Add operation must be succeessful.",
      sidechainWalletBoxStorage.update(version1.data, wbList1 ++ wbList2 ++ wbList3, List[Array[Byte]]()).isSuccess)

    //Test storage content
    assertEquals("Storage must have specified version.", version1, sidechainWalletBoxStorage.lastVesrionId.get())
    var wbl1 = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 9 items.", 9, wbl1.count(p => true))
    for(wb <- wbList1 ++ wbList2 ++ wbList3)
      assertTrue("Storage must contain all specified items.", wbl1.contains(wb))

    wbl1 = sidechainWalletBoxStorage.get(wbList1.map(_.box.id()))
    assertEquals("Storage must contain 3 RegularBoxes.", 3, wbl1.count(p => true))
    for(wb <- wbList1)
      assertTrue("Storage must contain all RegularBoxes.", wbl1.contains(wb))

    assertEquals("Storage must contain specified CustomBox.", wbList3.apply(0),
      sidechainWalletBoxStorage.get(wbList3.apply(0).box.id()).get)

    wbl1 = sidechainWalletBoxStorage.getByType(classOf[CertifierRightBox])
    assertEquals("Storage must contain 3 CertifierRightBoxes.", 3, wbl1.count(p => true))
    for(wb <- wbList2)
      assertTrue("Storage must contain all CertifierRightBoxes.", wbl1.contains(wb))

    assertEquals("Balances for CertifierRightBox must be same.", wbList2.map(_.box.value()).sum,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CertifierRightBox]))

    //SCENARIO - Test update/remove of WalletBoxes on non-empty storage
    assertTrue("Remove operation must be successful.",
      sidechainWalletBoxStorage.update(version2.data, List[WalletBox](), wbList2.map(_.box.id())).isSuccess)

    assertEquals("Storage must have specified version.", version2, sidechainWalletBoxStorage.lastVesrionId.get())
    wbl1 = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 6 items.", 6, wbl1.count(p => true))
    for(wb <- wbList1 ++ wbList3)
      assertTrue("Storage must contain all specified items (except removed).", wbl1.contains(wb))

    wbl1 = sidechainWalletBoxStorage.getByType(classOf[CertifierRightBox])
    assertEquals("Storage must not contain CertifierRightBoxes.", 0, wbl1.count(p => true))

    assertEquals("Balances for CertifierRightBox must be 0.", 0,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CertifierRightBox]))

    var rv = sidechainWalletBoxStorage.rollbackVersions
    assertEquals("Versions count in storage must be 2.", 2, rv.count(p => true))
    assertTrue("Storage must contain specified version.", rv.contains(version1))
    assertTrue("Storage must contain specified version.", rv.contains(version2))

    //SCENARIO - Test rollback functionality
    assertTrue("Rollback operation must be successful.", sidechainWalletBoxStorage.rollback(version1).isSuccess)

    assertEquals("Storage must have specified version.", version1, sidechainWalletBoxStorage.lastVesrionId.get())
    var wbl2 : List[WalletBox] = sidechainWalletBoxStorage.getAll
    assertEquals("Storage must contain 9 items.", 9, wbl2.count(p => true))
    for(wb <- wbList1 ++ wbList2 ++ wbList3)
      assertTrue("Storage must contain all specified items.", wbl2.contains(wb))

    assertEquals("Balances for CertifierRightBox must be same.", wbList2.map(_.box.value()).sum,
      sidechainWalletBoxStorage.getBoxesBalance(classOf[CertifierRightBox]))
  }

}

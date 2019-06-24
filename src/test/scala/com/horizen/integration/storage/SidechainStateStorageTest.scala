package com.horizen.integration.storage

import com.horizen._
import com.horizen.box._
import com.horizen.companion._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.proposition._
import com.horizen.storage._
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.collection.JavaConverters._
import scala.collection.mutable.Map

class SidechainStateStorageTest
  extends JUnitSuite
    with BoxFixture
    with IODBStoreFixture
    with SidechainTypes
{

  val customBoxesSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(CustomBox.BOX_TYPE_ID -> CustomBoxSerializer.getSerializer)
  val sidechainBoxesCompanion = new SidechainBoxesCompanion(customBoxesSerializers)

  @Test
  def mainFlowTest() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    val bList1 = getRegularBoxList(5).asScala.toSet
    val bList2 = getCustomBoxList(3).asScala.toSet
    val version1 = getVersion
    val version2 = getVersion

    //Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.",
      sidechainStateStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.",
      0, sidechainStateStorage.rollbackVersions.size)

    //Test insert operation (empty storage).
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(version1, (bList1 ++ bList2).asInstanceOf[Set[B]], Set()).isSuccess)

    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1 ++ bList2) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.get(b.id()).get)
    }

    //Test delete operation
    assertTrue("Update(delete) operation must be successful.",
      sidechainStateStorage.update(version2, Set(), bList1.slice(0, 1).map(_.id()) ++ bList2.slice(0, 1).map(_.id())).isSuccess)

    assertEquals("Version in storage must be - " + version1,
      version2, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 versions.",
      2, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1.slice(1, bList1.size) ++ bList2.slice(1, bList2.size)) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.get(b.id()).get)
    }

    for (b <- bList1.slice(0, 1) ++ bList2.slice(0, 1)) {
      assertTrue("Storage must not contain specified box - " + b,
        sidechainStateStorage.get(b.id()).isEmpty)
    }

    //Test rollback operation
    assertTrue("Rollback operation must be successful.",
      sidechainStateStorage.rollback(version1).isSuccess)

    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1 ++ bList2) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.get(b.id()).get)
    }
  }

  @Test
  def testExceptions() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    val bList1 = getRegularBoxList(5).asScala.toSet
    val version1 = getVersion

    //Try to rollback to non-existent version
    assertTrue("Rollback operation to non-existent version must throw exception.",
      sidechainStateStorage.rollback(version1).isFailure)

    //Try to remove non-existent item
    assertFalse("Remove operation of non-existent item must not throw exception.",
      sidechainStateStorage.update(version1, Set(), bList1.map(_.id())).isFailure)

  }


}

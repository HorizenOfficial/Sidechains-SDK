package com.horizen.integration.storage

import com.horizen.SidechainTypes
import com.horizen.fixtures.{BoxFixture, StoreFixture}
import com.horizen.librustsidechains.FieldElement
import com.horizen.storage.SidechainStateUtxoMerkleTreeStorage
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, UtxoMerkleTreeLeafInfo}
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.collection.JavaConverters._

class SidechainStateUtxoMerkleTreeStorageIntegrationTest
  extends JUnitSuite
    with BoxFixture
    with StoreFixture
    with SidechainTypes {

  @Test
  def mainFlowTest(): Unit = {
    val utxoStorage = new SidechainStateUtxoMerkleTreeStorage(getStorage())

    // Verify that there is no leaves info
    assertTrue("ForgerBox seq expected to be empty.", utxoStorage.getAllLeavesInfo.isEmpty)

    val zenBoxes: Seq[SidechainTypes#SCB] = getZenBoxList(5).asScala.map(_.asInstanceOf[SidechainTypes#SCB])
    val leavesInfoMap: Map[SidechainTypes#SCB, UtxoMerkleTreeLeafInfo] = zenBoxes.zipWithIndex.map {
      case (box, pos) =>
        val leafFE: FieldElement = utxoStorage.calculateLeaf(box)
        val leafInfo = UtxoMerkleTreeLeafInfo(leafFE.serializeFieldElement(), pos)
        leafFE.freeFieldElement()
        box -> leafInfo
    }.toMap

    val version1 = getVersion
    val version2 = getVersion


    // Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.", utxoStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.", 0, utxoStorage.rollbackVersions.size)


    // Test insert operation (empty storage).
    assertTrue("Update(insert) must be successful.", utxoStorage.update(version1, zenBoxes, Set()).isSuccess)
    assertEquals("Version in storage must be - " + version1, version1, utxoStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.", 1, utxoStorage.rollbackVersions.size)

    leavesInfoMap.foreach {
      case (box, expectedInfo) =>
        val leafInfoOpt = utxoStorage.getLeafInfo(box.id())
        assertTrue("Storage must contain specified leaf info for box " + box, leafInfoOpt.isDefined)

        assertEquals("Storage must contain different leaf info for box" + box, expectedInfo, leafInfoOpt.get)
    }

    assertEquals("Storage all leaves info is different", utxoStorage.getAllLeavesInfo.toSet, leavesInfoMap.values.toSet)


    // Store actual root for version1
    val version1UtxoRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)

    // Test insert operation (non-empty storage) and delete operation for the first ZenBox
    val anotherZenBoxes: Seq[SidechainTypes#SCB] = getZenBoxList(2).asScala.map(_.asInstanceOf[SidechainTypes#SCB])
    val boxIdsToRemoveSet: Set[ByteArrayWrapper] = Set(new ByteArrayWrapper(zenBoxes.head.id()))

    assertTrue("Update/delete operation must be successful.",
      utxoStorage.update(version2, anotherZenBoxes, boxIdsToRemoveSet).isSuccess)

    assertEquals("Version in storage must be - " + version2, version2, utxoStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 versions.", 2, utxoStorage.rollbackVersions.size)

    // Consider the removed first leaf
    val anotherLeavesPositions: Seq[Int] = Seq(0, zenBoxes.size)

    val anotherLeavesInfoMap: Map[SidechainTypes#SCB, UtxoMerkleTreeLeafInfo] = anotherZenBoxes.zip(anotherLeavesPositions).map {
      case (box, pos) =>
        val leafFE: FieldElement = utxoStorage.calculateLeaf(box)
        val leafInfo = UtxoMerkleTreeLeafInfo(leafFE.serializeFieldElement(), pos)
        leafFE.freeFieldElement()
        box -> leafInfo
    }.toMap

    val allLeavesMap = anotherLeavesInfoMap ++ (leavesInfoMap - zenBoxes.head)
    allLeavesMap.foreach {
      case (box, expectedInfo) =>
        val leafInfoOpt = utxoStorage.getLeafInfo(box.id())
        assertTrue("Storage must contain specified leaf info for box " + box, leafInfoOpt.isDefined)

        assertEquals("Storage must contain different leaf info for box" + box, expectedInfo, leafInfoOpt.get)
    }

    // Check that leaves were removed properly
    for(boxId <- boxIdsToRemoveSet) {
      assertTrue("Storage must not contain specified leaf info for box id " + boxId, utxoStorage.getLeafInfo(boxId.data).isEmpty)
    }

    assertEquals("Storage all leaves info is different", utxoStorage.getAllLeavesInfo.toSet, allLeavesMap.values.toSet)


    //Test rollback operation
    assertTrue("Rollback operation must be successful.", utxoStorage.rollback(version1).isSuccess)

    assertEquals("Version in storage must be - " + version1, version1, utxoStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.", 1, utxoStorage.rollbackVersions.size)

    // Check storage data to be consistent
    leavesInfoMap.foreach {
      case (box, expectedInfo) =>
        val leafInfoOpt = utxoStorage.getLeafInfo(box.id())
        assertTrue("Storage must contain specified leaf info for box " + box, leafInfoOpt.isDefined)

        assertEquals("Storage must contain different leaf info for box" + box, expectedInfo, leafInfoOpt.get)
    }

    assertEquals("Storage all leaves info is different", utxoStorage.getAllLeavesInfo.toSet, leavesInfoMap.values.toSet)

    val utxoRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)
    assertEquals("Different utxo merkle tree root expected after rollback.", version1UtxoRoot, utxoRoot)
  }

  @Test
  def testExceptions(): Unit = {
    val utxoStorage = new SidechainStateUtxoMerkleTreeStorage(getStorage())

    val version1 = getVersion

    //Try to rollback to non-existent version
    assertTrue("Rollback operation to non-existent version must throw exception.",
      utxoStorage.rollback(version1).isFailure)
  }
}

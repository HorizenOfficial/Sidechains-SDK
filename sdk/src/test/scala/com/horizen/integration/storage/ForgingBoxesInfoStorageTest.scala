package com.horizen.integration.storage

import com.horizen.SidechainTypes
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.fixtures.{BoxFixture, IODBStoreFixture}
import com.horizen.storage.{ForgingBoxesInfoStorage, IODBStoreAdapter}
import com.horizen.utils.{BytesUtils, ForgingStakeMerklePathInfo, MerklePath}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import java.util.{ArrayList => JArrayList}

import com.horizen.box.ForgerBox


class ForgingBoxesInfoStorageTest extends JUnitSuite with IODBStoreFixture with SidechainTypes with BoxFixture {

  @Test
  def mainWorkflow(): Unit = {
    val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(new IODBStoreAdapter(getStore()))

    val updateVersion = getVersion
    val forgerBox = getForgerBox
    val forgerBoxesToAppend: Seq[ForgerBox] = Seq(forgerBox)

    // Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.",
      forgingBoxesMerklePathStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.",
      0, forgingBoxesMerklePathStorage.rollbackVersions.size)

    // Test updateForgerBoxes operation (empty storage).
    assertTrue("updateForgerBoxes must be successful.",
      forgingBoxesMerklePathStorage.updateForgerBoxes(updateVersion, forgerBoxesToAppend, Seq()).isSuccess)
    assertEquals("Different ForgerBox seq expected.", forgerBoxesToAppend, forgingBoxesMerklePathStorage.getForgerBoxes.get)

    // Test updateForgingStakeMerklePathInfo operation.
    val epochNumber = ConsensusEpochNumber @@ 2
    val boxMerklePathInfoSeq = Seq(
      ForgingStakeMerklePathInfo(
        ForgingStakeInfo(forgerBox.blockSignProposition(), forgerBox.vrfPubKey(), forgerBox.value()),
        new MerklePath(new JArrayList())
      )
    )
    assertTrue("updateForgingStakeMerklePathInfo must be successful.", forgingBoxesMerklePathStorage.updateForgingStakeMerklePathInfo(epochNumber, boxMerklePathInfoSeq).isSuccess)

    // Test retrieving of merkle path info seq for EXISTING epoch
    forgingBoxesMerklePathStorage.getForgingStakeMerklePathInfoForEpoch(epochNumber) match {
      case Some(merklePathInfoSeq) => assertEquals("MerklePathInfoSeq expected to be equal to the original one.", boxMerklePathInfoSeq, merklePathInfoSeq)
      case None => fail(s"MerklePathInfoSeq expected to be present in storage for epoch $epochNumber.")
    }

    // Test retrieving of merkle path info seq for MISSED epoch
    val missedEpochNumber = ConsensusEpochNumber @@ 3
    assertTrue(s"MerklePathInfoSeq expected to be NOT present in storage for epoch $missedEpochNumber.",
      forgingBoxesMerklePathStorage.getForgingStakeMerklePathInfoForEpoch(missedEpochNumber).isEmpty)


    // Test rollback operation
    assertTrue("Rollback operation must be successful.", forgingBoxesMerklePathStorage.rollback(updateVersion).isSuccess)
    assertEquals("Version in storage must be - " + updateVersion, updateVersion, forgingBoxesMerklePathStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.", 1, forgingBoxesMerklePathStorage.rollbackVersions.size)
    assertTrue(s"MerklePathInfoSeq expected to be NOT present in storage for epoch $epochNumber.",
      forgingBoxesMerklePathStorage.getForgingStakeMerklePathInfoForEpoch(epochNumber).isEmpty)
  }

  @Test
  def updateForgerBoxes(): Unit = {
    val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(new IODBStoreAdapter(getStore()))

    // Test 1: Update empty storage with 3 new forger boxes
    val version1 = getVersion
    val forgerBox1 = getForgerBox
    val forgerBox2 = getForgerBox
    val forgerBox3 = getForgerBox
    val forgerBoxesToAppend: Seq[ForgerBox] = Seq(forgerBox1, forgerBox2, forgerBox3)

    assertTrue("updateForgerBoxes must be successful.",
      forgingBoxesMerklePathStorage.updateForgerBoxes(version1, forgerBoxesToAppend, Seq()).isSuccess)
    assertEquals("Different ForgerBox seq expected.", forgerBoxesToAppend, forgingBoxesMerklePathStorage.getForgerBoxes.get)


    // Test 2: Update non empty storage with 2 new forger boxes and 2 boxes to remove
    val version2 = getVersion
    val forgerBox4 = getForgerBox
    val forgerBox5 = getForgerBox

    assertTrue("updateForgerBoxes must be successful.",
      forgingBoxesMerklePathStorage.updateForgerBoxes(version2, Seq(forgerBox4, forgerBox5), Seq(forgerBox1.id(), forgerBox3.id())).isSuccess)
    assertEquals("Different ForgerBox seq expected.", Seq(forgerBox2, forgerBox4, forgerBox5), forgingBoxesMerklePathStorage.getForgerBoxes.get)
  }
}

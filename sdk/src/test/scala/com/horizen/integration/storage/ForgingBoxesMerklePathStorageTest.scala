package com.horizen.integration.storage

import com.horizen.SidechainTypes
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.fixtures.{BoxFixture, IODBStoreFixture}
import com.horizen.storage.{ForgingBoxesMerklePathStorage, IODBStoreAdapter}
import com.horizen.utils.{BytesUtils, ForgerBoxMerklePathInfo, MerklePath}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import java.util.{ArrayList => JArrayList}


class ForgingBoxesMerklePathStorageTest extends JUnitSuite with IODBStoreFixture with SidechainTypes with BoxFixture {

  @Test
  def mainWorkflow(): Unit = {
    val forgingBoxesMerklePathStorage = new ForgingBoxesMerklePathStorage(new IODBStoreAdapter(getStore()))

    val updateVersion = getVersion

    // Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.",
      forgingBoxesMerklePathStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.",
      0, forgingBoxesMerklePathStorage.rollbackVersions.size)

    // Test updateVersion operation (empty storage).
    assertTrue("UpdateVersion must be successful.", forgingBoxesMerklePathStorage.updateVersion(updateVersion).isSuccess)

    // Test update operation.
    val epochNumber = ConsensusEpochNumber @@ 2
    val boxMerklePathInfoSeq = Seq(
      ForgerBoxMerklePathInfo(
        getForgerBox,
        new MerklePath(new JArrayList())
      )
    )
    assertTrue("Update must be successful.", forgingBoxesMerklePathStorage.update(epochNumber, boxMerklePathInfoSeq).isSuccess)

    // Test retrieving of merkle path info seq for EXISTING epoch
    forgingBoxesMerklePathStorage.getInfoForEpoch(epochNumber) match {
      case Some(merklePathInfoSeq) => assertEquals("MerklePathInfoSeq expected to be equal to the original one.", boxMerklePathInfoSeq, merklePathInfoSeq)
      case None => fail(s"MerklePathInfoSeq expected to be present in storage for epoch $epochNumber.")
    }

    // Test retrieving of merkle path info seq for MISSED epoch
    val missedEpochNumber = ConsensusEpochNumber @@ 3
    assertTrue(s"MerklePathInfoSeq expected to be NOT present in storage for epoch $missedEpochNumber.",
      forgingBoxesMerklePathStorage.getInfoForEpoch(missedEpochNumber).isEmpty)


    // Test rollback operation
    assertTrue("Rollback operation must be successful.", forgingBoxesMerklePathStorage.rollback(updateVersion).isSuccess)
    assertEquals("Version in storage must be - " + updateVersion, updateVersion, forgingBoxesMerklePathStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.", 1, forgingBoxesMerklePathStorage.rollbackVersions.size)
    assertTrue(s"MerklePathInfoSeq expected to be NOT present in storage for epoch $epochNumber.",
      forgingBoxesMerklePathStorage.getInfoForEpoch(epochNumber).isEmpty)
  }
}

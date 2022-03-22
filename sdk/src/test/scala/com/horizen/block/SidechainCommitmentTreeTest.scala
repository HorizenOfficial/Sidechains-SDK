package com.horizen.block

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.fixtures.SecretFixture
import com.horizen.librustsidechains.{Constants, FieldElement}
import com.horizen.merkletreenative.MerklePath
import com.horizen.transaction.mainchain.ForwardTransfer
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar

import scala.util.Random

class SidechainCommitmentTreeTest extends JUnitSuite with MockitoSugar with SecretFixture {

  def getWithPadding(bytes: Array[Byte]): Array[Byte] =
    Bytes.concat(new Array[Byte](32 - bytes.length), bytes)

  def getSidechains(sidechainsCount: Int): (Seq[ByteArrayWrapper], ByteArrayWrapper, ByteArrayWrapper, ByteArrayWrapper) = {
    val beforeLeftmostSidechainId = new ByteArrayWrapper(
      getWithPadding(
        Bytes.concat(
          BytesUtils.fromHexString("abcdef"),
          Ints.toByteArray(1)
        )
      )
    )

    val afterRightmostSidechainId = new ByteArrayWrapper(
      getWithPadding(
        Bytes.concat(
          BytesUtils.fromHexString("abcdef"),
          Ints.toByteArray(sidechainsCount*2 + 1)
        )
      )
    )

    val innerMissedSidechainId = new ByteArrayWrapper(
      getWithPadding(
        Bytes.concat(
          BytesUtils.fromHexString("abcdef"),
          Ints.toByteArray(sidechainsCount)
        )
      )
    )

    val sidechainIdSeq = (2 to sidechainsCount * 2 by 2)
      .map(v =>
        Bytes.concat(
          BytesUtils.fromHexString("abcdef"),
          Ints.toByteArray(v)
        )
      )
      .map(v => new ByteArrayWrapper(getWithPadding(v)))

    (sidechainIdSeq, beforeLeftmostSidechainId, innerMissedSidechainId, afterRightmostSidechainId)
  }

  @Test
  def addNeighbourProofs(): Unit = {
    val commitmentTree = new SidechainCommitmentTree()

    val (sidechainIdSeq, beforeLeftmostSidechainId, innerMissedSidechainId, afterRightmostSidechainId) = getSidechains(11)

    sidechainIdSeq.foreach(scId => {
      val output = new MainchainTxForwardTransferCrosschainOutput(new Array[Byte](1), scId.data,
        Random.nextInt(10000), getMCPublicKeyHashProposition.bytes(), getMcReturnAddress)
      val forwardTransferHash = new Array[Byte](32)
      Random.nextBytes(forwardTransferHash)
      val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
      commitmentTree.addForwardTransfer(ft)
    })

    val commitmentOpt = commitmentTree.getCommitment
    assertTrue("Tree commitment must exist", commitmentOpt.isDefined)

    val commitmentBE = BytesUtils.reverseBytes(commitmentOpt.get)

    val absenceProof1 = commitmentTree.getAbsenceProof(beforeLeftmostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof1.isDefined)
    assertTrue("Absence proof must be valid.",
      SidechainCommitmentTree.verifyAbsenceProof(beforeLeftmostSidechainId.data, absenceProof1.get, commitmentBE))

    val absenceProof2 = commitmentTree.getAbsenceProof(innerMissedSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof2.isDefined)
    assertTrue("Absence proof must be valid.",
      SidechainCommitmentTree.verifyAbsenceProof(innerMissedSidechainId.data, absenceProof2.get, commitmentBE))

    val absenceProof3 = commitmentTree.getAbsenceProof(afterRightmostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof3.isDefined)
    assertTrue("Absence proof must be valid.",
      SidechainCommitmentTree.verifyAbsenceProof(afterRightmostSidechainId.data, absenceProof3.get, commitmentBE))

    commitmentTree.free()
  }

  @Test
  def existenceProofs(): Unit = {
    val commitmentTree = new SidechainCommitmentTree()

    val (sidechainIdSeq, _, _, _) = getSidechains(11)

    sidechainIdSeq.foreach(scId => {
      val output = new MainchainTxForwardTransferCrosschainOutput(new Array[Byte](1), scId.data,
        Random.nextInt(10000), getMCPublicKeyHashProposition.bytes(), getMcReturnAddress)
      val forwardTransferHash = new Array[Byte](32)
      Random.nextBytes(forwardTransferHash)
      val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
      commitmentTree.addForwardTransfer(ft)
    })

    val commitmentOpt = commitmentTree.getCommitment
    assertTrue("Tree commitment must exist", commitmentOpt.isDefined)

    val commitmentBE = BytesUtils.reverseBytes(commitmentOpt.get)

    sidechainIdSeq.foreach(scId => {
      val scCommitmentOpt = commitmentTree.getSidechainCommitment(scId.data)
      assertTrue("Sidechain commitment must exist.", scCommitmentOpt.isDefined)
      val existenceProof = commitmentTree.getExistenceProof(scId.data)
      assertTrue("Existence proof must exist.", existenceProof.isDefined)

      assertTrue("Absence proof must be valid.",
        SidechainCommitmentTree.verifyExistenceProof(scCommitmentOpt.get, existenceProof.get, commitmentBE))
    })

    commitmentTree.free()
  }

  @Test
  def merklePath(): Unit = {
    val commitmentTree = new SidechainCommitmentTree()
    val scId = FieldElementUtils.randomFieldElementBytes(12345)

    // Test FT merkle path
    val output = new MainchainTxForwardTransferCrosschainOutput(new Array[Byte](1), scId,
      Random.nextInt(10000), getMCPublicKeyHashProposition.bytes(), getMcReturnAddress)
    val forwardTransferHash = new Array[Byte](32)
    Random.nextBytes(forwardTransferHash)
    val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))

    assertTrue("FT must be added.", commitmentTree.addForwardTransfer(ft))

    val ftCommitmentOpt = commitmentTree.getFtCommitment(scId)
    assertTrue("FT commitment should exist.", ftCommitmentOpt.isDefined)
    val ftMerklePathOpt = commitmentTree.getForwardTransferMerklePath(scId, 0)
    assertTrue("FT merkle path should exist.", ftCommitmentOpt.isDefined)

    val ftLeaf = FieldElement.deserialize(commitmentTree.getFtLeaves(scId).head)
    val ftCommitment = FieldElement.deserialize(ftCommitmentOpt.get)
    val ftMerklePath = MerklePath.deserialize(ftMerklePathOpt.get)
    assertTrue("FT merkle path is inconsistent to the leaf and root.",
      ftMerklePath.verify(ftLeaf, ftCommitment))
    assertTrue("FT merkle path is inconsistent to the merkle tree height.",
      ftMerklePath.verify(Constants.SC_COMM_TREE_FT_SUBTREE_HEIGHT(), ftLeaf, ftCommitment))

    ftLeaf.freeFieldElement()
    ftCommitment.freeFieldElement()
    ftMerklePath.freeMerklePath()

    // Test SC merkle path
    val scCommitmentOpt = commitmentTree.getSidechainCommitment(scId)
    assertTrue("SC commitment should exist.", scCommitmentOpt.isDefined)
    val scCommitmentMerklePathOpt = commitmentTree.getSidechainCommitmentMerklePath(scId)
    assertTrue("SC commitment merkle path should exist.", scCommitmentMerklePathOpt.isDefined)
    val commitmentOpt = commitmentTree.getCommitment
    assertTrue("Commitment should exist.", commitmentOpt.isDefined)

    val scCommitment = FieldElement.deserialize(scCommitmentOpt.get)
    val commitment = FieldElement.deserialize(commitmentOpt.get)
    val scCommitmentMerklePath = MerklePath.deserialize(scCommitmentMerklePathOpt.get)
    assertTrue("SC merkle path is inconsistent to the leaf and root.",
      scCommitmentMerklePath.verify(scCommitment, commitment))
    assertTrue("SC merkle path is inconsistent to the merkle tree height.",
      scCommitmentMerklePath.verify(Constants.SC_COMM_TREE_HEIGHT(), scCommitment, commitment))

    scCommitment.freeFieldElement()
    commitment.freeFieldElement()
    scCommitmentMerklePath.freeMerklePath()
  }
}

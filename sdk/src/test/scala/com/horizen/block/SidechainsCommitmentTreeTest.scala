package com.horizen.block

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.fixtures.SecretFixture
import com.horizen.transaction.mainchain.ForwardTransfer
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar

import scala.util.Random

class SidechainsCommitmentTreeTest extends JUnitSuite with MockitoSugar with SecretFixture {

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
        Random.nextInt(10000), getMCPublicKeyHashProposition.bytes())
      val forwardTransferHash = new Array[Byte](32)
      Random.nextBytes(forwardTransferHash)
      val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
      commitmentTree.addForwardTransfer(ft)
    })

    val commitment = commitmentTree.getCommitment
    assertTrue("Tree commitment must exist", commitment.isDefined)

    val absenceProof1 = commitmentTree.getAbsenceProof(beforeLeftmostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof1.isDefined)
    assertTrue("Absence proof must be valid.",
      SidechainCommitmentTree.verifyAbsenceProof(beforeLeftmostSidechainId.data, absenceProof1.get, commitment.get))

    val absenceProof2 = commitmentTree.getAbsenceProof(innerMissedSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof2.isDefined)
    assertTrue("Absence proof must be valid.",
      SidechainCommitmentTree.verifyAbsenceProof(innerMissedSidechainId.data, absenceProof2.get, commitment.get))

    val absenceProof3 = commitmentTree.getAbsenceProof(afterRightmostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof3.isDefined)
    assertTrue("Absence proof must be valid.",
      SidechainCommitmentTree.verifyAbsenceProof(afterRightmostSidechainId.data, absenceProof3.get, commitment.get))
  }

  @Test
  def existenceProofs(): Unit = {
    val commitmentTree = new SidechainCommitmentTree()

    val (sidechainIdSeq, _, _, _) = getSidechains(11)

    sidechainIdSeq.foreach(scId => {
      val output = new MainchainTxForwardTransferCrosschainOutput(new Array[Byte](1), scId.data,
        Random.nextInt(10000), getMCPublicKeyHashProposition.bytes())
      val forwardTransferHash = new Array[Byte](32)
      Random.nextBytes(forwardTransferHash)
      val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
      commitmentTree.addForwardTransfer(ft)
    })

    val commitment = commitmentTree.getCommitment
    assertTrue("Tree commitment must exist", commitment.isDefined)

    sidechainIdSeq.foreach(scId => {
      val scCommitmentOpt = commitmentTree.getSidechainCommitment(scId)
      assertTrue("Sidechain commitment must exist.", scCommitmentOpt.isDefined)
      val existenceProof = commitmentTree.getExistenceProof(scId.data)
      assertTrue("Existence proof must exist.", existenceProof.isDefined)

      assertTrue("Absence proof must be valid.",
        SidechainCommitmentTree.verifyExistenceProof(scCommitmentOpt.get, existenceProof.get, commitment.get))
    })
  }
}

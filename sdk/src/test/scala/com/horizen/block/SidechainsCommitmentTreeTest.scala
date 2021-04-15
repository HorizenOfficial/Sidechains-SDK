package com.horizen.block

import java.util
import com.google.common.primitives.{Bytes, Ints}
import com.horizen.transaction.mainchain.ForwardTransfer
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.mockito.Mockito
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar

import scala.util.Random

class SidechainsCommitmentTreeTest extends JUnitSuite with MockitoSugar {

  private val size = 32;

  def getWithPadding(bytes: Array[Byte]): Array[Byte] =
    Bytes.concat(new Array[Byte](32 - bytes.length), bytes)

  def getSidechains(sidechainsCount: Int): (Seq[ByteArrayWrapper], ByteArrayWrapper, ByteArrayWrapper, ByteArrayWrapper) = {
    val beforeLeftMostSidechainId = new ByteArrayWrapper(
      getWithPadding(
        Bytes.concat(
          BytesUtils.fromHexString("abcdef"),
          Ints.toByteArray(1)
        )
      )
    )

    val afterRightMostSidechainId = new ByteArrayWrapper(
      getWithPadding(
        Bytes.concat(
          BytesUtils.fromHexString("abcdef"),
          Ints.toByteArray(sidechainsCount*2 + 1)
        )
      )
    )

    val innerSidechainId = new ByteArrayWrapper(
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

    (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId)
  }

  @Test
  def addNeighbourProofs(): Unit = {
    val shm = new SidechainCommitmentTree()

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(11)

    sidechainIdSeq.foreach(_ => {
      val output = mock[MainchainTxForwardTransferCrosschainOutput]
      val forwardTransferHash = new Array[Byte](32)
      Random.nextBytes(forwardTransferHash)
      Mockito.when(output.hash).thenReturn(forwardTransferHash)
      val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
      shm.addForwardTransfer(ft)
    })

    val treeCommitment = shm.getTreeCommitment();
    assertTrue("Tree commitment must exist", treeCommitment.isDefined)

    val absenceProof1 = shm.getAbsenceProof(beforeLeftMostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof1.isDefined)
    assertTrue("Absence proof must be valid.", SidechainCommitmentTree.verifyAbsenceProof(beforeLeftMostSidechainId.data, absenceProof1.get, treeCommitment.get))

    val absenceProof2 = shm.getAbsenceProof(innerSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof2.isDefined)
    assertTrue("Absence proof must be valid.", SidechainCommitmentTree.verifyAbsenceProof(beforeLeftMostSidechainId.data, absenceProof2.get, treeCommitment.get))

    val absenceProof3 = shm.getAbsenceProof(afterRightMostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof3.isDefined)
    assertTrue("Absence proof must be valid.", SidechainCommitmentTree.verifyAbsenceProof(beforeLeftMostSidechainId.data, absenceProof3.get, treeCommitment.get))
  }

  @Test
  def existenceProofs(): Unit = {
    val shm = new SidechainCommitmentTree()

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(11)

    sidechainIdSeq.foreach(scId => {
      val output = mock[MainchainTxForwardTransferCrosschainOutput]
      val forwardTransferHash = new Array[Byte](32)
      Random.nextBytes(forwardTransferHash)
      Mockito.when(output.hash).thenReturn(forwardTransferHash)
      val ft = new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
      shm.addForwardTransfer(ft)
    })

    val treeCommitment = shm.getTreeCommitment();
    assertTrue("Tree commitment must exist", treeCommitment.isDefined)

    val absenceProof1 = shm.getAbsenceProof(beforeLeftMostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof1.isDefined)
    assertTrue("Absence proof must be valid.", SidechainCommitmentTree.verifyAbsenceProof(beforeLeftMostSidechainId.data, absenceProof1.get, treeCommitment.get))

    val absenceProof2 = shm.getAbsenceProof(innerSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof2.isDefined)
    assertTrue("Absence proof must be valid.", SidechainCommitmentTree.verifyAbsenceProof(beforeLeftMostSidechainId.data, absenceProof2.get, treeCommitment.get))

    val absenceProof3 = shm.getAbsenceProof(afterRightMostSidechainId.data)

    assertTrue("Absence proof must exist.", absenceProof3.isDefined)
    assertTrue("Absence proof must be valid.", SidechainCommitmentTree.verifyAbsenceProof(beforeLeftMostSidechainId.data, absenceProof3.get, treeCommitment.get))
  }
}

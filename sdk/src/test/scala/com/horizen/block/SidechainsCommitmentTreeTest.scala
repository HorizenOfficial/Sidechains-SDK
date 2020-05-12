package com.horizen.block

import java.util

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.util.Random

class SidechainsCommitmentTreeTest extends JUnitSuite {

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
  def addNeighbourProofs1(): Unit = {

    val shm = new SidechainsCommitmentTree()

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(11)

    sidechainIdSeq.foreach(v => {
      val forwardTransferMerkleRootHash = new Array[Byte](size)
      Random.nextBytes(forwardTransferMerkleRootHash)
      shm.addForwardTransferMerkleRootHash(v, forwardTransferMerkleRootHash)
    })

    val merkleTree = shm.getMerkleTree

    val (lProof1, rProof1) = shm.getNeighbourSidechainCommitmentEntryProofs(beforeLeftMostSidechainId)

    assertTrue("Proof for left neighbour must not exist.", lProof1.isEmpty)
    assertTrue("Proof for right neighbour must exist.", rProof1.isDefined)

    assertTrue("Right neighbour must be leftmost", rProof1.get.merklePath.isLeftmost)
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof1.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(rProof1.get))
      )
    )

    val (lProof2, rProof2) = shm.getNeighbourSidechainCommitmentEntryProofs(innerSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof2.isDefined)
    assertTrue("Proof for right neighbour must exist.", rProof2.isDefined)

    assertEquals("Left neighbour must have leaf index 4", 4, lProof2.get.merklePath.leafIndex())
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof2.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof2.get))
      )
    )

    assertEquals("Right neighbour must have leaf index 5", 5, rProof2.get.merklePath.leafIndex())
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof2.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(rProof2.get))
      )
    )

    val (lProof3, rProof3) = shm.getNeighbourSidechainCommitmentEntryProofs(afterRightMostSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof3.isDefined)
    assertTrue("Proof for right neighbour must not exist.", rProof3.isEmpty)

    assertTrue("Left neighbour must be rightmost",
      lProof3.get.merklePath.isRightmost(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof3.get)))
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof3.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof3.get))
      )
    )

  }

  @Test
  def addNeighbourProofs2(): Unit = {

    val shm = new SidechainsCommitmentTree()

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(23)

    sidechainIdSeq.foreach(v => {
      val forwardTransferMerkleRootHash = new Array[Byte](size)
      Random.nextBytes(forwardTransferMerkleRootHash)
      shm.addForwardTransferMerkleRootHash(v, forwardTransferMerkleRootHash)
    })

    val merkleTree = shm.getMerkleTree

    val (lProof1, rProof1) = shm.getNeighbourSidechainCommitmentEntryProofs(beforeLeftMostSidechainId)

    assertTrue("Proof for left neighbour must not exist.", lProof1.isEmpty)
    assertTrue("Proof for right neighbour must exist.", rProof1.isDefined)

    assertTrue("Right neighbour must be leftmost", rProof1.get.merklePath.isLeftmost)
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof1.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(rProof1.get))
      )
    )

    val (lProof2, rProof2) = shm.getNeighbourSidechainCommitmentEntryProofs(innerSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof2.isDefined)
    assertTrue("Proof for right neighbour must exist.", rProof2.isDefined)

    assertEquals("Left neighbour must have leaf index 4", 10, lProof2.get.merklePath.leafIndex())
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof2.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof2.get))
      )
    )

    assertEquals("Right neighbour must have leaf index 5", 11, rProof2.get.merklePath.leafIndex())
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof2.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(rProof2.get))
      )
    )

    val (lProof3, rProof3) = shm.getNeighbourSidechainCommitmentEntryProofs(afterRightMostSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof3.isDefined)
    assertTrue("Proof for right neighbour must not exist.", rProof3.isEmpty)

    assertTrue("Left neighbour must be rightmost",
      lProof3.get.merklePath.isRightmost(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof3.get)))
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof3.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof3.get))
      )
    )

  }

  @Test
  def addNeighbourProofs3(): Unit = {

    val shm = new SidechainsCommitmentTree()

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(47)

    sidechainIdSeq.foreach(v => {
      val forwardTransferMerkleRootHash = new Array[Byte](size)
      Random.nextBytes(forwardTransferMerkleRootHash)
      shm.addForwardTransferMerkleRootHash(v, forwardTransferMerkleRootHash)
    })

    val merkleTree = shm.getMerkleTree

    val (lProof1, rProof1) = shm.getNeighbourSidechainCommitmentEntryProofs(beforeLeftMostSidechainId)

    assertTrue("Proof for left neighbour must not exist.", lProof1.isEmpty)
    assertTrue("Proof for right neighbour must exist.", rProof1.isDefined)

    assertTrue("Right neighbour must be leftmost", rProof1.get.merklePath.isLeftmost)
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof1.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(rProof1.get))
      )
    )

    val (lProof2, rProof2) = shm.getNeighbourSidechainCommitmentEntryProofs(innerSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof2.isDefined)
    assertTrue("Proof for right neighbour must exist.", rProof2.isDefined)

    assertEquals("Left neighbour must have leaf index 22", 22, lProof2.get.merklePath.leafIndex())
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof2.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof2.get))
      )
    )

    assertEquals("Right neighbour must have leaf index 23", 23, rProof2.get.merklePath.leafIndex())
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof2.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(rProof2.get))
      )
    )

    val (lProof3, rProof3) = shm.getNeighbourSidechainCommitmentEntryProofs(afterRightMostSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof3.isDefined)
    assertTrue("Proof for right neighbour must not exist.", rProof3.isEmpty)

    assertTrue("Left neighbour must be rightmost",
      lProof3.get.merklePath.isRightmost(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof3.get)))
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof3.get.merklePath.apply(SidechainCommitmentEntry.getSidechainCommitmentEntryHash(lProof3.get))
      )
    )

  }

}

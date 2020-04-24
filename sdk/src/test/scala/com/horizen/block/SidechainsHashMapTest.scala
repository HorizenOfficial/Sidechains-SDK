package com.horizen.block

import java.util

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.util.Random

class SidechainsHashMapTest extends JUnitSuite {

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
  def testaddNeighbourProofs1(): Unit = {

    val shm = new SidechainsHashMap()

    val transactionHash = new Array[Byte](size)

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(11)

    sidechainIdSeq.foreach(v => {
      Random.nextBytes(transactionHash)
      shm.addTransactionHashes(v, Seq(transactionHash))
    })

    val merkleTree = shm.getMerkleTree
    val fullMerkleTree = shm.getFullMerkleTree

    assertTrue("Root hashes must be the same.", util.Arrays.equals(merkleTree.rootHash(), fullMerkleTree.rootHash()))

    val (lProof1, rProof1) = shm.getNeighbourProofs(beforeLeftMostSidechainId)

    assertTrue("Proof for left neighbour must not exist.", lProof1.isEmpty)
    assertTrue("Proof for right neighbour must exist.", rProof1.isDefined)

    assertTrue("Right neighbour must be leftmost", rProof1.get.merklePath.isLeftmost)
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof1.get.merklePath.apply(SidechainHashList.getSidechainHash(rProof1.get))
      )
    )

    val (lProof2, rProof2) = shm.getNeighbourProofs(innerSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof2.isDefined)
    assertTrue("Proof for right neighbour must exist.", rProof2.isDefined)

    assertEquals("Left neighbour must have leaf index 4", 4, lProof2.get.merklePath.leafIndex())
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof2.get.merklePath.apply(SidechainHashList.getSidechainHash(lProof2.get))
      )
    )

    assertEquals("Right neighbour must have leaf index 5", 5, rProof2.get.merklePath.leafIndex())
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof2.get.merklePath.apply(SidechainHashList.getSidechainHash(rProof2.get))
      )
    )

    val (lProof3, rProof3) = shm.getNeighbourProofs(afterRightMostSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof3.isDefined)
    assertTrue("Proof for right neighbour must not exist.", rProof3.isEmpty)

    assertTrue("Left neighbour must be rightmost", lProof3.get.merklePath.isRightmost)
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof3.get.merklePath.apply(SidechainHashList.getSidechainHash(lProof3.get))
      )
    )

  }

  @Test
  def testaddNeighbourProofs2(): Unit = {

    val shm = new SidechainsHashMap()

    val transactionHash = new Array[Byte](size)

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(23)

    sidechainIdSeq.foreach(v => {
      Random.nextBytes(transactionHash)
      shm.addTransactionHashes(v, Seq(transactionHash))
    })

    val merkleTree = shm.getMerkleTree
    val fullMerkleTree = shm.getFullMerkleTree

    assertTrue("Root hashes must be the same.", util.Arrays.equals(merkleTree.rootHash(), fullMerkleTree.rootHash()))

    val (lProof1, rProof1) = shm.getNeighbourProofs(beforeLeftMostSidechainId)

    assertTrue("Proof for left neighbour must not exist.", lProof1.isEmpty)
    assertTrue("Proof for right neighbour must exist.", rProof1.isDefined)

    assertTrue("Right neighbour must be leftmost", rProof1.get.merklePath.isLeftmost)
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof1.get.merklePath.apply(SidechainHashList.getSidechainHash(rProof1.get))
      )
    )

    val (lProof2, rProof2) = shm.getNeighbourProofs(innerSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof2.isDefined)
    assertTrue("Proof for right neighbour must exist.", rProof2.isDefined)

    assertEquals("Left neighbour must have leaf index 4", 10, lProof2.get.merklePath.leafIndex())
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof2.get.merklePath.apply(SidechainHashList.getSidechainHash(lProof2.get))
      )
    )

    assertEquals("Right neighbour must have leaf index 5", 11, rProof2.get.merklePath.leafIndex())
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof2.get.merklePath.apply(SidechainHashList.getSidechainHash(rProof2.get))
      )
    )

    val (lProof3, rProof3) = shm.getNeighbourProofs(afterRightMostSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof3.isDefined)
    assertTrue("Proof for right neighbour must not exist.", rProof3.isEmpty)

    assertTrue("Left neighbour must be rightmost", lProof3.get.merklePath.isRightmost)
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof3.get.merklePath.apply(SidechainHashList.getSidechainHash(lProof3.get))
      )
    )

  }

  @Test
  def testaddNeighbourProofs3(): Unit = {

    val shm = new SidechainsHashMap()

    val transactionHash = new Array[Byte](size)

    val (sidechainIdSeq, beforeLeftMostSidechainId, innerSidechainId, afterRightMostSidechainId) = getSidechains(47)

    sidechainIdSeq.foreach(v => {
      Random.nextBytes(transactionHash)
      shm.addTransactionHashes(v, Seq(transactionHash))
    })

    val merkleTree = shm.getMerkleTree
    val fullMerkleTree = shm.getFullMerkleTree

    assertTrue("Root hashes must be the same.", util.Arrays.equals(merkleTree.rootHash(), fullMerkleTree.rootHash()))

    val (lProof1, rProof1) = shm.getNeighbourProofs(beforeLeftMostSidechainId)

    assertTrue("Proof for left neighbour must not exist.", lProof1.isEmpty)
    assertTrue("Proof for right neighbour must exist.", rProof1.isDefined)

    assertTrue("Right neighbour must be leftmost", rProof1.get.merklePath.isLeftmost)
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof1.get.merklePath.apply(SidechainHashList.getSidechainHash(rProof1.get))
      )
    )

    val (lProof2, rProof2) = shm.getNeighbourProofs(innerSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof2.isDefined)
    assertTrue("Proof for right neighbour must exist.", rProof2.isDefined)

    assertEquals("Left neighbour must have leaf index 22", 22, lProof2.get.merklePath.leafIndex())
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof2.get.merklePath.apply(SidechainHashList.getSidechainHash(lProof2.get))
      )
    )

    assertEquals("Right neighbour must have leaf index 23", 23, rProof2.get.merklePath.leafIndex())
    assertTrue("Right neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        rProof2.get.merklePath.apply(SidechainHashList.getSidechainHash(rProof2.get))
      )
    )

    val (lProof3, rProof3) = shm.getNeighbourProofs(afterRightMostSidechainId)

    assertTrue("Proof for left neighbour must exist.", lProof3.isDefined)
    assertTrue("Proof for right neighbour must not exist.", rProof3.isEmpty)

    assertTrue("Left neighbour must be rightmost", lProof3.get.merklePath.isRightmost)
    assertTrue("Left neighbour proof must be valid.",
      util.Arrays.equals(merkleTree.rootHash(),
        lProof3.get.merklePath.apply(SidechainHashList.getSidechainHash(lProof3.get))
      )
    )

  }

}

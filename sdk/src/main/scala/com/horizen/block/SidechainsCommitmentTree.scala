package com.horizen.block

import com.horizen.transaction.mainchain.{BwtRequest, ForwardTransfer, SidechainCreation}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerklePath, MerkleTree}

import scala.collection.JavaConverters._
import scala.collection.mutable

class SidechainsCommitmentTree
{
  val sidechainsHashMap: mutable.Map[ByteArrayWrapper, SidechainCommitmentEntry] = new mutable.HashMap[ByteArrayWrapper, SidechainCommitmentEntry]()

  def addCswInput(sidechainId: ByteArrayWrapper, csw: MainchainTxCswCrosschainInput): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.addCswInput(csw)
      case None =>
        val entry = new SidechainCommitmentEntry()
        entry.addCswInput(csw)
        sidechainsHashMap.put(sidechainId, entry)
    }
  }

  def addSidechainCreation(sidechainId: ByteArrayWrapper, sc: SidechainCreation): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.addSidechainCreation(sc)
      case None =>
        val entry = new SidechainCommitmentEntry()
        entry.addSidechainCreation(sc)
        sidechainsHashMap.put(sidechainId, entry)
    }
  }

  def addForwardTransfer(sidechainId: ByteArrayWrapper, ft: ForwardTransfer): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.addForwardTransfer(ft)
      case None =>
        val entry = new SidechainCommitmentEntry()
        entry.addForwardTransfer(ft)
        sidechainsHashMap.put(sidechainId, entry)
    }
  }

  def addBwtRequest(sidechainId: ByteArrayWrapper, btr: BwtRequest): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.addBwtRequest(btr)
      case None =>
        val entry = new SidechainCommitmentEntry()
        entry.addBwtRequest(btr)
        sidechainsHashMap.put(sidechainId, entry)
    }
  }

  def addCertificate(certificate: WithdrawalEpochCertificate): Unit = {
    val sidechainId = new ByteArrayWrapper(certificate.sidechainId)
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.addCertificate(certificate)
      case None =>
        val entry = new SidechainCommitmentEntry()
        entry.addCertificate(certificate)
        sidechainsHashMap.put(sidechainId, entry)
    }
  }

  def getCertLeaves(sidechainId: ByteArrayWrapper): Seq[Array[Byte]] = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.getCertLeaves
      case None => Seq()
    }
  }

  def getSidechainCommitmentEntryHash(sidechainId: ByteArrayWrapper): Array[Byte] = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) => entry.getCommitment(sidechainId.data)
      case None => Array.emptyByteArray
    }
  }

  private[block] def getMerkleTree: MerkleTree = {
    val merkleTreeLeaves = getOrderedSidechainIds().map(id => getSidechainCommitmentEntryHash(id))
    MerkleTree.createMerkleTree(merkleTreeLeaves.asJava)
  }

  def getMerkleRoot: Array[Byte] = {
    getMerkleTree.rootHash()
  }

  def getSidechainCommitmentEntryMerklePath(sidechainId: ByteArrayWrapper): Option[MerklePath] = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(entry) =>
        val merkleTree = getMerkleTree
        val entryHash = new ByteArrayWrapper(entry.getCommitment(sidechainId.data))
        val leafIndex = merkleTree.leaves().asScala.map(l => new ByteArrayWrapper(l)).indexOf(entryHash)
        Some(merkleTree.getMerklePathForLeaf(leafIndex))
      case None => None
    }
  }

  def getNeighbourSidechainCommitmentEntryProofs(sidechainId: ByteArrayWrapper): (Option[SidechainCommitmentEntryProof], Option[SidechainCommitmentEntryProof]) = {
    // Collect and sort sidechain Ids
    val sidechainsIds: Seq[ByteArrayWrapper] = getOrderedSidechainIds()
    val littleEndianSidechainId = new ByteArrayWrapper(BytesUtils.reverseBytes(sidechainId.data))

    val leftNeighbourIndex: Int = sidechainsIds.lastIndexWhere(id => new ByteArrayWrapper(BytesUtils.reverseBytes(id.data)) < littleEndianSidechainId)
    val leftNeighbourProofOption = if(leftNeighbourIndex >= 0) {
      val leftNeighbourSidechainId: Array[Byte] = sidechainsIds(leftNeighbourIndex).data
      Some(getSidechainCommitmentEntryProof(leftNeighbourSidechainId, leftNeighbourIndex))
    } else {
      None
    }

    val rightNeighbourIndex: Int = sidechainsIds.indexWhere(id => new ByteArrayWrapper(BytesUtils.reverseBytes(id.data)) > littleEndianSidechainId, from = leftNeighbourIndex)
    val rightNeighbourProofOption = if(rightNeighbourIndex >= 0 ) {
      val rightNeighbourSidechainId: Array[Byte] = sidechainsIds(rightNeighbourIndex).data
      Some(getSidechainCommitmentEntryProof(rightNeighbourSidechainId, rightNeighbourIndex))
    } else {
      None
    }

    (leftNeighbourProofOption, rightNeighbourProofOption)
  }

  private def getSidechainCommitmentEntryProof(sidechainId: Array[Byte], leafIndex: Int): SidechainCommitmentEntryProof = {
    val merkleTree = getMerkleTree
    val entry = sidechainsHashMap(new ByteArrayWrapper(sidechainId))
    SidechainCommitmentEntryProof(sidechainId, entry.getTxsHash, entry.getCertCommitment, merkleTree.getMerklePathForLeaf(leafIndex))
  }

  // Sidechain ids are represented in a little-endian and ordered lexicographically same as in the MC.
  // Note: MC data in the SC represented as a big-endian.
  private def getOrderedSidechainIds(): Seq[ByteArrayWrapper] = {
    val littleEndianOrderedIds = sidechainsHashMap.map(entry => new ByteArrayWrapper(BytesUtils.reverseBytes(entry._1.data))).toSeq.sortWith(_ < _)
    littleEndianOrderedIds.map(id => new ByteArrayWrapper(BytesUtils.reverseBytes(id.data)))
  }
}
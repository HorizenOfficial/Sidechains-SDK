package com.horizen.block


import com.google.common.primitives.{Bytes}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerklePath, MerkleTree, Utils}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class SidechainHashList
{
  private var forwardTransfersHash: Option[Array[Byte]] = None
  private var withdrawalCertificateHash: Option[Array[Byte]] = None

  def setForwardTransfersHash(hash: Array[Byte]): Unit = {
    forwardTransfersHash = Some(hash)
  }

  def setWithdrawalCertificateHash(hash: Array[Byte]): Unit = {
    withdrawalCertificateHash = Some(hash)
  }

  def getTxsHash: Array[Byte] = {
    val backwardMerkleRoot = SidechainHashList.MAGIC_SC_STRING
    BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(forwardTransfersHash.getOrElse(SidechainHashList.MAGIC_SC_STRING)),
          BytesUtils.reverseBytes(backwardMerkleRoot)
        )
      )
    )
  }

  def getSidechainHash(sidechainId: Array[Byte]): Array[Byte] = {
    SidechainHashList.getSidechainHash(
      sidechainId,
      getTxsHash,
      withdrawalCertificateHash.getOrElse(SidechainHashList.MAGIC_SC_STRING)
    )
  }

  def getNeighbourProof(sidechainId: Array[Byte], merkleTree: MerkleTree): NeighbourProof = {
    val mkr = new ByteArrayWrapper(getSidechainHash(sidechainId))
    val leafIndex = merkleTree.leaves().asScala.map(l => new ByteArrayWrapper(l)).lastIndexOf(mkr)
    NeighbourProof(sidechainId, getTxsHash, withdrawalCertificateHash.getOrElse(SidechainHashList.MAGIC_SC_STRING),
      merkleTree.getMerklePathForLeaf(leafIndex))
  }
}

object SidechainHashList
{
  private val MAGIC_SC_STRING = BytesUtils.fromHexString("bc99f1efa1a15584ced657631202ec3642eb89d4533c8a9cd58875146b867f4e")

  private def getSidechainHash(sidechainId: Array[Byte], txsHash: Array[Byte], wcertHash: Array[Byte]): Array[Byte] = {
    BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(txsHash),
          BytesUtils.reverseBytes(wcertHash),
          BytesUtils.reverseBytes(sidechainId)
        )
      )
    )
  }

  def getSidechainHash(proof: NeighbourProof): Array[Byte] = {
    getSidechainHash(proof.sidechainId, proof.txsHash, proof.wcertHash)
  }
}

class SidechainsHashMap
{
  val sidechainsHashMap: mutable.Map[ByteArrayWrapper, SidechainHashList] = new mutable.HashMap[ByteArrayWrapper, SidechainHashList]()

  def addForwardTransferMerkleRootHash(sidechainId: ByteArrayWrapper, rootHash: Array[Byte]): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) => shl.setForwardTransfersHash(rootHash)
      case None =>
        val shl = new SidechainHashList()
        shl.setForwardTransfersHash(rootHash)
        sidechainsHashMap.put(sidechainId, shl)
    }
  }

  def addCertificate(certificate: MainchainBackwardTransferCertificate): Unit = {
    val sidechainId = new ByteArrayWrapper(certificate.sidechainId)
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) => shl.setWithdrawalCertificateHash(certificate.hash)
      case None =>
        val shl = new SidechainHashList()
        shl.setWithdrawalCertificateHash(certificate.hash)
        sidechainsHashMap.put(sidechainId, shl)
    }
  }

  def getSidechainHash(sidechainId: ByteArrayWrapper): Array[Byte] = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) => shl.getSidechainHash(sidechainId.data)
      case None => Array.emptyByteArray
    }
  }

  private[block] def getMerkleTree: MerkleTree = {
    val merkleTreeLeaves = sidechainsHashMap.toSeq.sortWith(_._1 < _._1)
      .map(pair => {
        getSidechainHash(pair._1)
      })

    MerkleTree.createMerkleTree(merkleTreeLeaves.asJava)
  }

  def getMerkleRoot: Array[Byte] = {
    getMerkleTree.rootHash()
  }

  def getMerklePath(sidechainId: ByteArrayWrapper): Option[MerklePath] = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) =>
        val mkt = getMerkleTree
        val mkr = new ByteArrayWrapper(getSidechainHash(sidechainId))
        val leafIndex = mkt.leaves().asScala.map(l => new ByteArrayWrapper(l)).indexOf(mkr)
        Some(mkt.getMerklePathForLeaf(leafIndex))
      case None => None
    }
  }

  def getNeighbourProofs(sidechainId: ByteArrayWrapper): (Option[NeighbourProof], Option[NeighbourProof]) = {
    val scl: Seq[ByteArrayWrapper] = sidechainsHashMap.get(sidechainId) match {
      case Some(s) => sidechainsHashMap.map(_._1).toSeq.sortWith(_ < _)
      case None => (sidechainsHashMap.map(_._1).toSeq :+ sidechainId).sortWith(_ < _)
    }

    val merkleTree = getMerkleTree

    val rightNeighbourOption = Try(scl(scl.indexOf(sidechainId) + 1))
      .toOption match {
      case Some(schId) => sidechainsHashMap.get(schId) match {
        case Some(schhl) => Some(schhl.getNeighbourProof(schId.data, merkleTree))
        case None => None
      }
      case None => None
    }
    val leftNeighbourOption = Try(scl(scl.indexOf(sidechainId) - 1))
      .toOption match {
      case Some(schId) => sidechainsHashMap.get(schId) match {
        case Some(schhl) => Some(schhl.getNeighbourProof(schId.data, merkleTree))
        case None => None
      }
      case None => None
    }
    (leftNeighbourOption, rightNeighbourOption)
  }
}
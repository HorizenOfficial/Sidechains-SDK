package com.horizen.block


import com.google.common.primitives.{Bytes}
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerklePath, MerkleTree, Utils}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

object MerkleTreeUtils {

  //returns count of leves on the lowest level of full binary tree
  def getFullSize(leavesCount: Int): Int = {
    var i = 1
    while (i < leavesCount)
      i *= 2
    i
  }

  //returns height of binary tree for specified count of leaves
  def getTreeHeight(leavesCount: Int): Int = {
    var size = getFullSize(leavesCount)
    var treeHeight = 1
    while (size > 1) {
      treeHeight += 1
      size /= 2
    }

    treeHeight
  }

  def getPaddingCount(treeHeight: Int, level: Int): Int = {
    var currentLevel = level
    var paddingCount = 1
    while (currentLevel < treeHeight) {
      currentLevel += 1
      paddingCount *= 2
    }
    paddingCount
  }

}

class SidechainHashList
{
  private val transactionHash: mutable.ListBuffer[Array[Byte]] = new mutable.ListBuffer[Array[Byte]]()
  private var withdrawalCertificateHash: Option[Array[Byte]] = None

  def addTransactionHash(hashSeq: Seq[Array[Byte]]): Unit = {
    transactionHash.appendAll(hashSeq)
  }

  def addWithdrawalCertificateHash(hash: Array[Byte]): Unit = {
    withdrawalCertificateHash = Some(hash)
  }

  def getTxsHash: Array[Byte] = {
    val transactionMerkleRoot =
      if (transactionHash.nonEmpty)
        MerkleTree.createMerkleTree(transactionHash.asJava).rootHash()
      else
        SidechainHashList.MAGIC_SC_STRING
    val backwardMerkleRoot = SidechainHashList.MAGIC_SC_STRING


    BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(transactionMerkleRoot),
          BytesUtils.reverseBytes(backwardMerkleRoot),
        )
      )
    )
  }

  def getSidechainHash (sidechainId: Array[Byte]): Array[Byte] = {

    val txsHash: Array[Byte] = getTxsHash

    val scHash: Array[Byte] = SidechainHashList.getSidechainHash(sidechainId,
      txsHash, withdrawalCertificateHash.getOrElse(SidechainHashList.MAGIC_SC_STRING))

    scHash
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

  def addTransactionOutputs(sidechainId: ByteArrayWrapper,
                            outputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) => shl.addTransactionHash(outputs.map(_.hash()))
      case None =>
        val shl = new SidechainHashList()
        shl.addTransactionHash(outputs.map(_.hash()))
        sidechainsHashMap.put(sidechainId, shl)
    }
  }

  def addCertificate(certificate: MainchainBackwardTransferCertificate): Unit = {
    val sidechainId = new ByteArrayWrapper(certificate.sidechainId)
    val certificateHash =
        BytesUtils.reverseBytes(
          Utils.doubleSHA256Hash(
            certificate.certificateBytes
          )
        )
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) =>shl.addWithdrawalCertificateHash(certificateHash)
      case None =>
        val shl = new SidechainHashList()
        shl.addWithdrawalCertificateHash(certificateHash)
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

  private[block] def getFullMerkleTree: MerkleTree = {
    var merkleTreeLeaves = sidechainsHashMap.toSeq.sortWith(_._1 < _._1)
      .map(pair => {
        getSidechainHash(pair._1)
      })

    val fullMerkleTreeSize = MerkleTreeUtils.getFullSize(merkleTreeLeaves.size)
    val fullMerkleTreeHeight = MerkleTreeUtils.getTreeHeight(merkleTreeLeaves.size)

    while (merkleTreeLeaves.size < fullMerkleTreeSize) {
      var currentLevel = fullMerkleTreeHeight
      var paddingCount = 0
      var currentLevelLeavesCount = merkleTreeLeaves.size
      while (currentLevel > 1 && paddingCount == 0) {
        if (currentLevelLeavesCount %2 == 1) {
          paddingCount = MerkleTreeUtils.getPaddingCount(fullMerkleTreeHeight, currentLevel)
        } else {
          currentLevel -= 1
          currentLevelLeavesCount /= 2
        }
      }
      merkleTreeLeaves = merkleTreeLeaves ++ merkleTreeLeaves.slice(merkleTreeLeaves.size - paddingCount, merkleTreeLeaves.size)
    }

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
        case Some(schhl) if rightNeighbourOption.isDefined => Some(schhl.getNeighbourProof(schId.data, merkleTree))
        case Some(schhl) if rightNeighbourOption.isEmpty => Some(schhl.getNeighbourProof(schId.data, getFullMerkleTree))
        case None => None
      }
      case None => None
    }
    (leftNeighbourOption, rightNeighbourOption)
  }

  //for tests
  private[block] def addTransactionHashes(sidechainId: ByteArrayWrapper,
                                          transactionHashSeq: Seq[Array[Byte]]): Unit = {
    sidechainsHashMap.get(sidechainId) match {
      case Some(shl) => shl.addTransactionHash(transactionHashSeq)
      case None =>
        val shl = new SidechainHashList()
        shl.addTransactionHash(transactionHashSeq)
        sidechainsHashMap.put(sidechainId, shl)
    }
  }

}
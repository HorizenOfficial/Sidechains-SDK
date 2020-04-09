package com.horizen.block


import com.google.common.primitives.{Bytes}
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerklePath, MerkleTree, Utils}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

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
      leafIndex, merkleTree.getMerklePathForLeaf(leafIndex))
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

  private def getMerkleTree: MerkleTree = {
    val merkleTreeLeaves = sidechainsHashMap.toSeq.sortWith(_._1 < _._1)
      .map(pair => {
        getSidechainHash(pair._1)
      })

    MerkleTree.createMerkleTree(merkleTreeLeaves.asJava)
  }

  private def getFullMerkleTree: MerkleTree = {
    var merkleTreeLeaves = sidechainsHashMap.toSeq.sortWith(_._1 < _._1)
      .map(pair => {
        getSidechainHash(pair._1)
      })

    var fullMktSize = 2
    while (fullMktSize < merkleTreeLeaves.size)
      fullMktSize *= 2

    while (merkleTreeLeaves.size < fullMktSize)
      merkleTreeLeaves = merkleTreeLeaves :+ merkleTreeLeaves.last

    MerkleTree.createMerkleTree(merkleTreeLeaves.asJava)
  }

  def getMerkleRoot: Array[Byte] = {
    getMerkleTree.rootHash()
  }

  def getMerklePath(sidechaiId: ByteArrayWrapper): Option[MerklePath] = {
    sidechainsHashMap.get(sidechaiId) match {
      case Some(shl) =>
        val mkt = getMerkleTree
        val mkr = new ByteArrayWrapper(getSidechainHash(sidechaiId))
        val leafIndex = mkt.leaves().asScala.map(l => new ByteArrayWrapper(l)).indexOf(mkr)
        Some(mkt.getMerklePathForLeaf(leafIndex))
      case None => None
    }
  }

  def getNeighborProofs(sidechainId: ByteArrayWrapper): (Option[NeighbourProof], Option[NeighbourProof]) = {
    val scl: Seq[ByteArrayWrapper] = sidechainsHashMap.get(sidechainId) match {
      case Some(s) => sidechainsHashMap.map(_._1).toSeq.sortWith(_ < _)
      case None => (sidechainsHashMap.map(_._1).toSeq :+ sidechainId).sortWith(_ < _)
    }

    val merkleTree = getMerkleTree

    val rightNeighborOption = Try(scl(scl.indexOf(sidechainId) + 1))
      .toOption match {
      case Some(schId) => sidechainsHashMap.get(schId) match {
        case Some(schhl) => Some(schhl.getNeighbourProof(schId.data, merkleTree))
        case None => None
      }
      case None => None
    }
    val leftNeighborOption = Try(scl(scl.indexOf(sidechainId) - 1))
      .toOption match {
      case Some(schId) => sidechainsHashMap.get(schId) match {
        case Some(schhl) if rightNeighborOption.isDefined => Some(schhl.getNeighbourProof(schId.data, merkleTree))
        case Some(schhl) if rightNeighborOption.isEmpty => Some(schhl.getNeighbourProof(schId.data, getFullMerkleTree))
        case None => None
      }
      case None => None
    }
    (leftNeighborOption, rightNeighborOption)
  }

}
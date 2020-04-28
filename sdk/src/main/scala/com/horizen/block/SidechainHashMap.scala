package com.horizen.block

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.SidechainTypes
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

  def getSidechainHash (sidechainId: Array[Byte]): Array[Byte] = {
    val transactionMerkleRoot =
      if (transactionHash.nonEmpty)
        MerkleTree.createMerkleTree(transactionHash.asJava).rootHash()
      else
        SidechainHashList.MAGIC_SC_STRING
    val backwardMerkleRoot = SidechainHashList.MAGIC_SC_STRING


    val txsHash: Array[Byte] = BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(transactionMerkleRoot),
          BytesUtils.reverseBytes(backwardMerkleRoot),
        )
      )
    )

    val scHash: Array[Byte] = BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(txsHash),
          BytesUtils.reverseBytes(withdrawalCertificateHash.getOrElse(SidechainHashList.MAGIC_SC_STRING)),
          BytesUtils.reverseBytes(sidechainId)
        )
      )
    )

    scHash
  }

}

object SidechainHashList
{
  /*
  private val MAGIC_SC_STRING: Array[Byte] = BytesUtils
    .reverseBytes(
      Utils.doubleSHA256Hash(
        BytesUtils.reverseBytes("Horizen ScTxsCommitment null hash string".getBytes)
      )
    )
  */

  private val MAGIC_SC_STRING = BytesUtils.fromHexString("bc99f1efa1a15584ced657631202ec3642eb89d4533c8a9cd58875146b867f4e")
}

class SidechainHashMap
{

  val sidechainHashMap: mutable.Map[ByteArrayWrapper, SidechainHashList] = new mutable.HashMap[ByteArrayWrapper, SidechainHashList]()

  def addTransactionOutputs(sidechainId: ByteArrayWrapper,
                            outputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]): SidechainHashMap = {
    sidechainHashMap.get(sidechainId) match {
      case Some(shl) => shl.addTransactionHash(outputs.map(_.hash()))
      case None =>
        val shl = new SidechainHashList()
        shl.addTransactionHash(outputs.map(_.hash()))
        sidechainHashMap.put(sidechainId, shl)
    }

    this
  }

  def addCertificate(certificate: MainchainBackwardTransferCertificate): SidechainHashMap = {
    val sidechainId = new ByteArrayWrapper(certificate.sidechainId)
    val certificateHash =
        BytesUtils.reverseBytes(
          Utils.doubleSHA256Hash(
            certificate.certificateBytes
          )
        )
    sidechainHashMap.get(sidechainId) match {
      case Some(shl) =>shl.addWithdrawalCertificateHash(certificateHash)
      case None =>
        val shl = new SidechainHashList()
        shl.addWithdrawalCertificateHash(certificateHash)
        sidechainHashMap.put(sidechainId, shl)
    }

    this
  }

  def getMerkleRoot(sidechainId: ByteArrayWrapper): Array[Byte] = {
    sidechainHashMap.get(sidechainId) match {
      case Some(shl) => shl.getSidechainHash(sidechainId.data)
      case None => Array.emptyByteArray
    }
  }

  def getMerkleRoot(sidechainId: Array[Byte]): Array[Byte] =
    getMerkleRoot(new ByteArrayWrapper(sidechainId))

  def getMerkleTree: MerkleTree = {
    val merkleTreeLeaves = sidechainHashMap.toSeq.sortWith(_._1 < _._1)
      .map(pair => {
        getMerkleRoot(pair._1)
      })

    MerkleTree.createMerkleTree(merkleTreeLeaves.asJava)
  }

  def getMerkleRoot: Array[Byte] = {
    getMerkleTree.rootHash()
  }

  def getMerklePath(sidechaiId: ByteArrayWrapper): Option[MerklePath] = {
    sidechainHashMap.get(sidechaiId) match {
      case Some(shl) =>
        val mkt = getMerkleTree
        val mkr = new ByteArrayWrapper(getMerkleRoot(sidechaiId))
        val leafIndex = mkt.leaves().asScala.map(l => new ByteArrayWrapper(l)).lastIndexOf(mkr)
        Some(mkt.getMerklePathForLeaf(leafIndex))
      case None => None
    }
  }

  def getNeighborsMerklePaths(sidechainId: ByteArrayWrapper): (Option[(Array[Byte], MerklePath)], Option[(Array[Byte], MerklePath)]) = {
    val scl: Seq[ByteArrayWrapper] = (sidechainHashMap.map(_._1).toSeq :+ sidechainId).sortWith(_ < _)
    val leftNeighborOption = Try(scl(scl.indexOf(sidechainId) - 1))
      .toOption match {
      case Some(s) => Some(getMerkleRoot(s), getMerklePath(s).get)
      case None => None
    }
    val rightNeighborOption = Try(scl(scl.indexOf(sidechainId) + 1))
      .toOption match {
      case Some(s) => Some(getMerkleRoot(s), getMerklePath(s).get)
      case None => None
    }
    (leftNeighborOption, rightNeighborOption)
  }

}
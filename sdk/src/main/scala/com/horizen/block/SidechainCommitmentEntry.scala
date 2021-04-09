package com.horizen.block

import com.google.common.primitives.Bytes
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.{BytesUtils, MerkleTree, Utils}

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

class SidechainCommitmentEntry
{
  private val ftLeaves: ListBuffer[Array[Byte]] = ListBuffer()
  private val certLeaves: ListBuffer[Array[Byte]] = ListBuffer()

  def addSidechainCreation(sc: SidechainCreation): Unit = {
    // In current MC implementation SC and FWT are stored and processed together
    ftLeaves.append(sc.hash)
  }

  def addForwardTransfer(ft: ForwardTransfer): Unit = {
    ftLeaves.append(ft.hash)
  }

  def addCertificate(cert: WithdrawalEpochCertificate): Unit = {
    certLeaves.append(cert.hash)
  }

  def addCertLeaf(leaf: Array[Byte]): Unit = {
    certLeaves.append(leaf)
  }

  def getFtCommitment: Array[Byte] = {
    if(ftLeaves.isEmpty)
      SidechainCommitmentEntry.MAGIC_SC_STRING
    else
      MerkleTree.createMerkleTree(ftLeaves.asJava).rootHash()
  }

  def getMbtrCommitment: Array[Byte] = {
    // MBTRs are not supported in current implementation.
    SidechainCommitmentEntry.MAGIC_SC_STRING
  }

  def getTxsHash: Array[Byte] = {
    BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(getFtCommitment),
          BytesUtils.reverseBytes(getMbtrCommitment)
        )
      )
    )
  }

  def getCertCommitment: Array[Byte] = {
    // TODO: In current implementation we take only top quality certificate hash
    // We are sure about the certificates order - from lowest to highest quality
    certLeaves.lastOption.getOrElse(SidechainCommitmentEntry.MAGIC_SC_STRING)
  }

  def getCertLeaves: Seq[Array[Byte]] = {
    certLeaves.toList
  }

  def getCommitment(sidechainId: Array[Byte]): Array[Byte] = {
    SidechainCommitmentEntry.getCommitment(
      sidechainId,
      getTxsHash,
      getCertCommitment
    )
  }
}

object SidechainCommitmentEntry
{
  val CERT_LEAF_LENGTH = 32 // bytes

  private val MAGIC_SC_STRING = BytesUtils.fromHexString("bc99f1efa1a15584ced657631202ec3642eb89d4533c8a9cd58875146b867f4e")

  private def getCommitment(sidechainId: Array[Byte], txsHash: Array[Byte], wcertHash: Array[Byte]): Array[Byte] = {
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

  def getCommitment(proof: SidechainCommitmentEntryProof): Array[Byte] = {
    getCommitment(proof.sidechainId, proof.txsHash, proof.wcertHash)
  }
}

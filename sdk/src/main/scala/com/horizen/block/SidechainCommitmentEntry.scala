package com.horizen.block

import com.google.common.primitives.Bytes
import com.horizen.utils.{BytesUtils, Utils}

class SidechainCommitmentEntry
{
  private var forwardTransfersHash: Option[Array[Byte]] = None
  private var withdrawalCertificateHash: Option[Array[Byte]] = None
  private val backwardTransferRequestHash: Option[Array[Byte]] = None

  def setForwardTransfersHash(hash: Array[Byte]): Unit = {
    forwardTransfersHash = Some(hash)
  }

  def setWithdrawalCertificateHash(hash: Array[Byte]): Unit = {
    withdrawalCertificateHash = Some(hash)
  }

  def getTxsHash: Array[Byte] = {
    BytesUtils.reverseBytes(
      Utils.doubleSHA256Hash(
        Bytes.concat(
          BytesUtils.reverseBytes(forwardTransfersHash.getOrElse(SidechainCommitmentEntry.MAGIC_SC_STRING)),
          BytesUtils.reverseBytes(backwardTransferRequestHash.getOrElse(SidechainCommitmentEntry.MAGIC_SC_STRING))
        )
      )
    )
  }

  def getWCertHash: Array[Byte] = {
    withdrawalCertificateHash.getOrElse(SidechainCommitmentEntry.MAGIC_SC_STRING)
  }

  def getSidechainCommitmentEntryHash(sidechainId: Array[Byte]): Array[Byte] = {
    SidechainCommitmentEntry.getSidechainCommitmentEntryHash(
      sidechainId,
      getTxsHash,
      getWCertHash
    )
  }
}

object SidechainCommitmentEntry
{
  private val MAGIC_SC_STRING = BytesUtils.fromHexString("bc99f1efa1a15584ced657631202ec3642eb89d4533c8a9cd58875146b867f4e")

  private def getSidechainCommitmentEntryHash(sidechainId: Array[Byte], txsHash: Array[Byte], wcertHash: Array[Byte]): Array[Byte] = {
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

  def getSidechainCommitmentEntryHash(proof: SidechainCommitmentEntryProof): Array[Byte] = {
    getSidechainCommitmentEntryHash(proof.sidechainId, proof.txsHash, proof.wcertHash)
  }
}

package com.horizen.mainchain.api

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.box.WithdrawalRequestBox
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import java.math.BigDecimal

import com.horizen.block.{BitVectorCertificateField, FieldElementCertificateField}
import com.horizen.utils.BytesUtils

@JsonView(Array(classOf[Views.Default]))
case class SidechainInfoResponse
  (sidechainId: Array[Byte],
   balance: String,
   creatingTxHash: Array[Byte],
   createdInBlock: Array[Byte],
   createdAtBlockHeight: Long,
   withdrawalEpochLength: Long
  )

@JsonView(Array(classOf[Views.Default]))
case class BackwardTransferEntry
  (pubkeyhash: Array[Byte],
   amount: String)
{
  require(pubkeyhash != null, "Address MUST be NOT NULL.")
}

@JsonView(Array(classOf[Views.Default]))
case class SendCertificateRequest
  (sidechainId: Array[Byte],
   epochNumber: Int,
   endEpochCumScTxCommTreeRoot: Array[Byte],
   proofBytes: Array[Byte],
   quality: Long,
   backwardTransfers: Seq[BackwardTransferEntry],
   fieldElementCertificateFields: Seq[String],
   bitVectorCertificateFields: Seq[String],
   ftrMinAmount: String,
   btrMinFee: String,
   fee: String = "0.00001")
{
  require(sidechainId.length == 32, "SidechainId MUST has length 32 bytes.")
  require(endEpochCumScTxCommTreeRoot != null, "End cumulative Sc Tx CommTree root hash MUST be NOT NULL.")
}

case class SendCertificateResponse
  (certificateId: Array[Byte])

case class GetRawCertificateRequest
  (certificateId: Array[Byte])

@JsonView(Array(classOf[Views.Default]))
case class GetRawCertificateResponse
  (hex: Array[Byte])

object CertificateRequestCreator {

  val ZEN_COINS_DIVISOR: BigDecimal = new BigDecimal(100000000)

  def create(sidechainId: Array[Byte],
             epochNumber: Int,
             endEpochCumulativeScTxCommTreeRoot: Array[Byte],
             proofBytes: Array[Byte],
             quality: Long,
             withdrawalRequestBoxes: Seq[WithdrawalRequestBox],
             ftMinAmount: Long,
             btrFee: Long) : SendCertificateRequest = {
    SendCertificateRequest(
      sidechainId,
      epochNumber,
      endEpochCumulativeScTxCommTreeRoot,
      proofBytes,
      quality,
      // Note: we should send BT entries public key hashes in reversed BE endianness.
      withdrawalRequestBoxes.map(wrb => BackwardTransferEntry(BytesUtils.reverseBytes(wrb.proposition().bytes()), new BigDecimal(wrb.value()).divide(ZEN_COINS_DIVISOR).toPlainString)),
      Seq(), // No custom field elements support for Threshold signature proof
      Seq(), // No bitvectors support for Threshold signature proofs
      new BigDecimal(ftMinAmount).divide(ZEN_COINS_DIVISOR).toPlainString,
      new BigDecimal(btrFee).divide(ZEN_COINS_DIVISOR).toPlainString
    )
  }
}
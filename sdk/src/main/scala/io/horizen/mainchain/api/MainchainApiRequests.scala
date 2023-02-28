package io.horizen.mainchain.api

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.certnative.BackwardTransfer
import io.horizen.params.NetworkParams
import io.horizen.json.Views
import io.horizen.utils.BytesUtils

import java.math.BigDecimal


@JsonView(Array(classOf[Views.Default]))
case class BackwardTransferEntry(address: String, amount: String)

@JsonView(Array(classOf[Views.Default]))
case class SendCertificateRequest
  (sidechainId: Array[Byte],
   epochNumber: Int,
   endEpochCumCommTreeHash: Array[Byte],
   proofBytes: Array[Byte],
   quality: Long,
   backwardTransfers: Seq[BackwardTransferEntry],
   fieldElementCertificateFields: Seq[Array[Byte]],
   bitVectorCertificateFields: Seq[Array[Byte]],
   ftrMinAmount: String,
   btrMinFee: String,
   fee: Option[String])
{
  require(sidechainId.length == 32, "SidechainId MUST has length 32 bytes.")
  require(endEpochCumCommTreeHash != null, "End epoch cumulative Sc Tx CommTree root hash MUST be NOT NULL.")
}

case class SendCertificateResponse
  (certificateId: Array[Byte])

object CertificateRequestCreator {

  val ZEN_COINS_DIVISOR: BigDecimal = new BigDecimal(100000000)

  def create(sidechainId: Array[Byte],
             epochNumber: Int,
             endEpochCumCommTreeHash: Array[Byte],
             proofBytes: Array[Byte],
             quality: Long,
             backwardTransfers: Seq[BackwardTransfer],
             ftMinAmount: Long,
             btrFee: Long,
             customFields: Seq[Array[Byte]],
             fee: Option[String],
             params: NetworkParams) : SendCertificateRequest = {
    SendCertificateRequest(
      // Note: we should send uint256 types in BE.
      BytesUtils.reverseBytes(sidechainId),
      epochNumber,
      endEpochCumCommTreeHash,
      proofBytes,
      quality,
      // Note: we should send BT entries public key hashes in reversed BE endianness.
      backwardTransfers.map(backwardTransfer => {
        val pubKeyAddress: String = BytesUtils.toHorizenPublicKeyAddress(backwardTransfer.getPublicKeyHash, params)
        BackwardTransferEntry(pubKeyAddress, new BigDecimal(backwardTransfer.getAmount()).divide(ZEN_COINS_DIVISOR).toPlainString)
      }),
      customFields,
      Seq(), // No bitvectors support for Threshold signature proofs
      new BigDecimal(ftMinAmount).divide(ZEN_COINS_DIVISOR).toPlainString,
      new BigDecimal(btrFee).divide(ZEN_COINS_DIVISOR).toPlainString,
      fee
    )

  }
}
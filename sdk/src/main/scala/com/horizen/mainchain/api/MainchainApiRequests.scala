package com.horizen.mainchain.api

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.box.WithdrawalRequestBox
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views

import java.math.BigDecimal
import com.horizen.block.{BitVectorCertificateField, FieldElementCertificateField}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.utils.BytesUtils

import scala.collection.convert.ImplicitConversions._

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

case class GetRawCertificateRequest
  (certificateId: Array[Byte])

@JsonView(Array(classOf[Views.Default]))
case class GetRawCertificateResponse
  (hex: Array[Byte])

object CertificateRequestCreator {

  val ZEN_COINS_DIVISOR: BigDecimal = new BigDecimal(100000000)

  def create(sidechainId: Array[Byte],
             epochNumber: Int,
             endEpochCumCommTreeHash: Array[Byte],
             proofBytes: Array[Byte],
             quality: Long,
             withdrawalRequestBoxes: Seq[WithdrawalRequestBox],
             ftMinAmount: Long,
             btrFee: Long,
             utxoMerkleTreeRoot: Array[Byte],
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
      withdrawalRequestBoxes.map(wrb => {
        val pubKeyAddress: String = BytesUtils.toHorizenPublicKeyAddress(wrb.proposition().bytes(), params)
        BackwardTransferEntry(pubKeyAddress, new BigDecimal(wrb.value()).divide(ZEN_COINS_DIVISOR).toPlainString)
      }),
      CryptoLibProvider.sigProofThresholdCircuitFunctions.splitUtxoMerkleTreeRoot(utxoMerkleTreeRoot).toSeq,
      Seq(), // No bitvectors support for Threshold signature proofs
      new BigDecimal(ftMinAmount).divide(ZEN_COINS_DIVISOR).toPlainString,
      new BigDecimal(btrFee).divide(ZEN_COINS_DIVISOR).toPlainString,
      fee
    )
  }
}
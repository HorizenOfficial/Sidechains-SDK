package com.horizen.mainchain

import com.fasterxml.jackson.annotation.JsonView

import com.horizen.box.WithdrawalRequestBox
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views

@JsonView(Array(classOf[Views.Default]))
case class SidechainInfoResponce
  (sidechainId: Array[Byte],
   balance: Double,
   creatingTxHash: Array[Byte],
   createdInBlock: Array[Byte],
   createdAtBlockHeight: Long,
   withdrawalEpochLength: Long
  )

@JsonView(Array(classOf[Views.Default]))
case class WithdrawalRequest
  (pubkeyhash: Array[Byte],
   amount: Double)
{
  require(pubkeyhash != null, "Address MUST be NOT NULL.")
  require(amount > 0, "Amount MUST be greater than 0.")
}

@JsonView(Array(classOf[Views.Default]))
case class CertificateRequest(
   sidechainId: Array[Byte],
   epochNumber: Int,
   endEpochBlockHash: Array[Byte],
   previousEpochEndBlockHash: Array[Byte],
   proofBytes: Array[Byte],
   withdrawalRequests: Seq[WithdrawalRequest],
   subtractFeeFromAmount: Boolean = false,
   fee: Double = 0.00001)
{
  require(sidechainId.length == 32, "SidechainId MUST has length 32 bytes.")
  require(endEpochBlockHash != null, "End epoch block hash MUST be NOT NULL.")
  require(withdrawalRequests != null, "List of WithdrawalRequests MUST be NOT NULL.")
  require(withdrawalRequests.nonEmpty, "List of WithdrawalRequests MUST be not empty.")
}

case class CertificateResponce
  (certificateId: Array[Byte])

case class RawCertificateRequest
  (certificateId: Array[Byte])

@JsonView(Array(classOf[Views.Default]))
case class RawCertificateResponce
  (hex: Array[Byte])

object CertificateRequestCreator {

  val ZEN_COINS_DIVIDOR = 100000000

  def create(epochNumber: Int,
             endEpochBlockHash: Array[Byte],
             previousEpochEndBlockHash: Array[Byte],
             proofBytes: Array[Byte],
             withdrawalRequestBoxes: Seq[WithdrawalRequestBox],
             params: NetworkParams) : CertificateRequest = {

    CertificateRequest(
      params.sidechainId,
      epochNumber,
      endEpochBlockHash,
      previousEpochEndBlockHash,
      proofBytes,
      withdrawalRequestBoxes.map(wrb => WithdrawalRequest(wrb.proposition().bytes(), wrb.value().toDouble/ZEN_COINS_DIVIDOR)))
  }
}
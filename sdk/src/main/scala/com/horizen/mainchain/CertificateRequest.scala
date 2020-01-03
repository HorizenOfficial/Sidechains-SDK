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
case class CertificateRequest
  (sidechainId: Array[Byte],
   withdrawalRequests: Seq[WithdrawalRequest])
{
  require(sidechainId.length == 32, "SidechainId MUST has length 32 bytes.")
  require(withdrawalRequests != null, "List of WithdrawalRequests MUST ne NOT NULL.")
  require(withdrawalRequests.nonEmpty, "List of WithdrawalRequests MUST be not empty.")
}

case class CertificateRequestResponce
  (certificateId: Array[Byte])

case class RawCertificate
  (certificateId: Array[Byte])

@JsonView(Array(classOf[Views.Default]))
case class RawCertificateResponce
  (hex: Array[Byte])

object CertificateRequestCreator {
  def create(withdrawalRequestBoxes: Seq[WithdrawalRequestBox], params: NetworkParams) : CertificateRequest = {
    CertificateRequest(params.sidechainId,
      for (wrb <- withdrawalRequestBoxes) yield WithdrawalRequest(wrb.proposition().bytes(), wrb.value()))
  }
}
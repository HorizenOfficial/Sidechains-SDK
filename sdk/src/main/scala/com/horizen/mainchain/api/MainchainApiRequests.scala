package com.horizen.mainchain.api

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.box.WithdrawalRequestBox
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import java.math.BigDecimal

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
   endEpochBlockHash: Array[Byte],
   backwardTransfers: Seq[BackwardTransferEntry],
   subtractFeeFromAmount: Boolean = false,
   fee: String = "0.00001")
{
  require(sidechainId.length == 32, "SidechainId MUST has length 32 bytes.")
  require(endEpochBlockHash != null, "End epoch block hash MUST be NOT NULL.")
  require(backwardTransfers != null, "List of BackwardTransfers MUST be NOT NULL.")
  require(backwardTransfers.nonEmpty, "List of BackwardTransfers MUST be not empty.")
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

  def create(epochNumber: Int, endEpochBlockHash: Array[Byte],
             withdrawalRequestBoxes: Seq[WithdrawalRequestBox],
             params: NetworkParams) : SendCertificateRequest = {
    SendCertificateRequest(params.sidechainId, epochNumber, endEpochBlockHash,
      withdrawalRequestBoxes.map(wrb => BackwardTransferEntry(wrb.proposition().bytes(), new BigDecimal(wrb.value()).divide(ZEN_COINS_DIVISOR).toPlainString)))
  }
}
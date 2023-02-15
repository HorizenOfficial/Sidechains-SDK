package com.horizen.account.state

import com.horizen.cryptolibprovider.utils.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.params.NetworkParams

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor]): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ maybeKeyRotationMsgProcessor.toSeq ++ customMessageProcessors
  }
}

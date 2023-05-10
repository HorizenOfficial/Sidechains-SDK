package io.horizen.account.state

import io.horizen.account.sc2sc.ScTxCommitmentTreeRootHashMessageProcessor
import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.Sc2ScUtils

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor]): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }
    // TODO: this condition will be replaced with the sc2sc proving and verification key paths existence that will be introduced with SDK-649
    val sc2ScMsgProcessors = if (Sc2ScUtils.isActive(params)) Seq(ScTxCommitmentTreeRootHashMessageProcessor())
                             else Seq()
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ maybeKeyRotationMsgProcessor.toSeq ++ sc2ScMsgProcessors ++ customMessageProcessors
  }
}

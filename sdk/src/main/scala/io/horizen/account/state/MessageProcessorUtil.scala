package io.horizen.account.state

import io.horizen.account.sc2sc.ScTxCommitmentTreeRootHashMessageProcessor
import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.Sc2ScUtils

object MessageProcessorUtil {
  private val scTxMsgProc = ScTxCommitmentTreeRootHashMessageProcessor

  def getScTxMsgProc: ScTxCommitmentTreeRootHashMessageProcessor.type = scTxMsgProc

  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor]): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }
    val sc2ScMsgProcessors = if (Sc2ScUtils.isActive(params)) Seq(scTxMsgProc)
                             else Seq()

    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ maybeKeyRotationMsgProcessor.toSeq ++ sc2ScMsgProcessors ++ customMessageProcessors
  }
}

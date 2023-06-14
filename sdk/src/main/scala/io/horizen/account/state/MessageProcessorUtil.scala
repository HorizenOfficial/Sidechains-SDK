package io.horizen.account.state

import io.horizen.account.sc2sc.ScTxCommitmentTreeRootHashMessageProcessor
import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.fork.{ForkManager, Sc2ScFork}
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.Sc2ScUtils
import io.horizen.utils.TimeToEpochUtils
import sparkz.core.utils.TimeProvider

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor], timeProvider: TimeProvider): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }
    val epoch = TimeToEpochUtils.timeStampToEpochNumber(params, timeProvider.time())
    val sc2ScMsgProcessors = if (Sc2ScUtils.isActive(params, ForkManager.getOptionalSidechainFork[Sc2ScFork](epoch))) Seq(ScTxCommitmentTreeRootHashMessageProcessor())
                             else Seq()
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ maybeKeyRotationMsgProcessor.toSeq ++ sc2ScMsgProcessors ++ customMessageProcessors
  }
}

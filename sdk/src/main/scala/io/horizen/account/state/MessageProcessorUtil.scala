package io.horizen.account.state

import io.horizen.account.sc2sc.ScTxCommitmentTreeRootHashMessageProcessor
import io.horizen.consensus.ConsensusEpochNumber
import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.fork.{ForkManager, Sc2ScFork}
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.Sc2ScUtils
import io.horizen.utils.TimeToEpochUtils
import sparkz.core.utils.TimeProvider

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor], consensusEpochNumber: Int): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }
    val sc2ScMsgProcessors = if (Sc2ScUtils.isActive(ForkManager.getOptionalSidechainFork[Sc2ScFork](consensusEpochNumber))) Seq(ScTxCommitmentTreeRootHashMessageProcessor())
                             else Seq()
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ maybeKeyRotationMsgProcessor.toSeq ++ sc2ScMsgProcessors ++ customMessageProcessors
  }
}

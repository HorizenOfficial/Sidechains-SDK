package com.horizen.certificatesubmitter.strategies

import com.horizen._
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.CertificateData
import com.horizen.chain.{MainchainHeaderInfo, SidechainBlockInfo}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.fork.ForkManager
import com.horizen.params.NetworkParams
import com.horizen.utils.{BytesUtils, TimeToEpochUtils}
import scorex.util.ScorexLogging
import sparkz.core.NodeViewHolder.CurrentView

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.util.Try

abstract class CircuitStrategy[T <: CertificateData](settings: SidechainSettings, params: NetworkParams) extends ScorexLogging{
  
  def generateProof(certificateData: T, provingFileAbsolutePath: String): com.horizen.utils.Pair[Array[Byte], java.lang.Long]

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  def buildCertificateData(sidechainNodeView: View, status: SignaturesStatus): T

  def getMessageToSign(view: View, referencedWithdrawalEpochNumber: Int): Try[Array[Byte]]

  // No MBTRs support, so no sense to specify btrFee different to zero.
  def getBtrFee(referencedWithdrawalEpochNumber: Int): Long = 0

  // Every positive value FT is allowed.
  protected [certificatesubmitter] def getFtMinAmount(consensusEpochNumber: Int): Long = {
    ForkManager.getSidechainConsensusEpochFork(consensusEpochNumber).ftMinAmount
  }

  protected def lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history: SidechainHistory, state: SidechainState, withdrawalEpochNumber: Int): Array[Byte] = {
    val headerInfo: MainchainHeaderInfo = getLastMainchainBlockInfoForWithdrawalEpochNumber(history, state, withdrawalEpochNumber)
    headerInfo.cumulativeCommTreeHash
  }

  protected def lastConsensusEpochNumberForWithdrawalEpochNumber(history: SidechainHistory, state: SidechainState, withdrawalEpochNumber: Int): ConsensusEpochNumber = {
    val headerInfo: MainchainHeaderInfo = getLastMainchainBlockInfoForWithdrawalEpochNumber(history, state, withdrawalEpochNumber)

    val parentBlockInfo: SidechainBlockInfo = history.storage.blockInfoById(headerInfo.sidechainBlockId)
    TimeToEpochUtils.timeStampToEpochNumber(params, parentBlockInfo.timestamp)
  }

  protected def getLastMainchainBlockInfoForWithdrawalEpochNumber(history: SidechainHistory, state: SidechainState, withdrawalEpochNumber: Int): MainchainHeaderInfo = {
    val mcBlockHash = {
      val withdrawalEpochLastMcBlockHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1

      val withdrawalEpochLastMcBlockHash = withdrawalEpochNumber match {
        case -1 => params.parentHashOfGenesisMainchainBlock
        case _ =>
          history.getMainchainBlockReferenceInfoByMainchainBlockHeight(withdrawalEpochLastMcBlockHeight).asScala
            .map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
      }

      if(params.isNonCeasing) {
        // For non-ceasing sidechain we may include previous epoch certificate after the end of its "virtual withdrawal
        // epoch" because of any delay reason: submitters were offline or MC had other data to be included with higher
        // priority, etc.
        // Mainchain has a specific timing check that every certificate references a block whose commitment
        // tree includes the previous certificate. That is applicable only for non-ceasing sidechains.
        // For ceasing ones it comes for free, because of the concept of "submission window".
        // So, in case of being non-ceasing one, certificate may refer to the `endEpochCumScTxCommTreeRoot` that belongs
        // to the MC block after the end of "virtual withdrawal epoch". So the higher mc block must be chosen.
        state.lastCertificateSidechainBlockId() match {
          case Some(blockId) =>
            val block = history.modifierById(blockId).getOrElse(throw new IllegalStateException(s"Missed sc block $blockId in the history."))
            // Get mc block hash with the top quality certificate from the block.
            // Note: sc block may contain multiple MC block ref data with certs for different epochs (for example: ... N-2, N-1, N),
            // but we are sure that exactly the LAST certificate for epoch N is always the one we are interested at.
            val mcBlockHashWithCert: Array[Byte] = block.mainchainBlockReferencesData
              .reverse
              .find(data => data.topQualityCertificate.isDefined)
              .getOrElse(throw new IllegalStateException(s"top quality certificate was not found for given sc block $blockId"))
              .headerHash
          val certSubmissionHeight: Int = history.getMainchainHeaderInfoByHash(mcBlockHashWithCert).asScala
            .getOrElse(throw new IllegalStateException(s"Missed MC header info."))
            .height

            if(certSubmissionHeight > withdrawalEpochLastMcBlockHeight) {
              // Certificate has been submitted after the corresponding "virtual withdrawal epoch" end.
              mcBlockHashWithCert
            } else {
              // Certificate has been submitted in-time.
              withdrawalEpochLastMcBlockHash
            }
          case None =>
            // First certificate case
            withdrawalEpochLastMcBlockHash
        }
      } else {
        // Ceasing sidechain case - behave as usual
        withdrawalEpochLastMcBlockHash
      }
    }

    log.debug(s"Last MC block hash for withdrawal epoch number $withdrawalEpochNumber is ${
      BytesUtils.toHexString(mcBlockHash)
    }")

    history.mainchainHeaderInfoByHash(mcBlockHash).getOrElse(throw new IllegalStateException("Missed MC Cumulative Hash"))
  }
}

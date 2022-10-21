package com.horizen.certificatesubmitter.strategies

import akka.util.Timeout
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

import java.io.File
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

abstract class KeyRotationStrategy(settings: SidechainSettings, params: NetworkParams) extends ScorexLogging{

  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  def generateProof(certificateData: CertificateData): com.horizen.utils.Pair[Array[Byte], java.lang.Long]

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  def buildCertificateData(sidechainNodeView: View, status: SignaturesStatus): CertificateData

  def getMessageToSign(view: View, referencedWithdrawalEpochNumber: Int): Try[Array[Byte]]

  // No MBTRs support, so no sense to specify btrFee different to zero.
  def getBtrFee(referencedWithdrawalEpochNumber: Int): Long = 0

  // Every positive value FT is allowed.
  protected [certificatesubmitter] def getFtMinAmount(consensusEpochNumber: Int): Long = {
    ForkManager.getSidechainConsensusEpochFork(consensusEpochNumber).ftMinAmount
  }

  protected def lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Array[Byte] = {
    val headerInfo: MainchainHeaderInfo = getLastMainchainBlockInfoForWithdrawalEpochNumber(history, withdrawalEpochNumber)
    headerInfo.cumulativeCommTreeHash
  }

  protected def lastConsensusEpochNumberForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): ConsensusEpochNumber = {
    val headerInfo: MainchainHeaderInfo = getLastMainchainBlockInfoForWithdrawalEpochNumber(history, withdrawalEpochNumber)

    val parentBlockInfo: SidechainBlockInfo = history.storage.blockInfoById(headerInfo.sidechainBlockId)
    TimeToEpochUtils.timeStampToEpochNumber(params, parentBlockInfo.timestamp)
  }

  protected def getLastMainchainBlockInfoForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): MainchainHeaderInfo = {
    val mcBlockHash = withdrawalEpochNumber match {
      case -1 => params.parentHashOfGenesisMainchainBlock
      case _ =>
        val mcHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1
        history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala
          .map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
    }
    log.debug(s"Last MC block hash for withdrawal epoch number $withdrawalEpochNumber is ${
      BytesUtils.toHexString(mcBlockHash)
    }")

    history.mainchainHeaderInfoByHash(mcBlockHash).getOrElse(throw new IllegalStateException("Missed MC Cumulative Hash"))
  }

  val provingFileAbsolutePath: String = {
    if (params.certProvingKeyFilePath.isEmpty) {
      throw new IllegalStateException(s"Proving key file name is not set")
    }

    val provingFile: File = new File(params.certProvingKeyFilePath)
    if (!provingFile.canRead) {
      throw new IllegalStateException(s"Proving key file at path ${provingFile.getAbsolutePath} is not exist or can't be read")
      ""
    } else {
      log.debug(s"Found proving key file at location: ${provingFile.getAbsolutePath}")
      provingFile.getAbsolutePath
    }
  }
}

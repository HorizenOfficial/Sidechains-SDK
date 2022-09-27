package com.horizen.certificatesubmitter.strategies

import akka.util.Timeout
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.DataForProofGeneration
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils
import com.horizen._
import scorex.util.ScorexLogging
import sparkz.core.NodeViewHolder.CurrentView

import java.io.File
import java.util.Optional
import scala.compat.java8.OptionConverters.{RichOptionForJava8, RichOptionalGeneric}
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

abstract class KeyRotationStrategy(settings: SidechainSettings, params: NetworkParams) extends ScorexLogging{

  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long]

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGeneration

  def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]]

  // No MBTRs support, so no sense to specify btrFee different to zero.
  def getBtrFee(referencedWithdrawalEpochNumber: Int): Long = 0

  // Every positive value FT is allowed.
  protected def getFtMinAmount(referencedWithdrawalEpochNumber: Int): Long = 0

  protected def getUtxoMerkleTreeRoot(referencedWithdrawalEpochNumber: Int, state: SidechainState): Seq[Array[Byte]] = {
    if (params.isCSWEnabled) {
      state.utxoMerkleTreeRoot(referencedWithdrawalEpochNumber) match {
        case x: Some[Array[Byte]] => x.toSeq
        case None =>
          log.error("UtxoMerkleTreeRoot is not defined even if CSW is enabled")
          throw new IllegalStateException("UtxoMerkleTreeRoot is not defined")
      }
    }
    else {
      Seq.empty[Array[Byte]]
    }
  }

  protected def lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Array[Byte] = {
    val mcBlockHash = withdrawalEpochNumber match {
      case -1 => params.parentHashOfGenesisMainchainBlock
      case _ => {
        val mcHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1
        history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala.map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
      }
    }
    log.debug(s"Last MC block hash for withdrawal epoch number $withdrawalEpochNumber is ${
      BytesUtils.toHexString(mcBlockHash)
    }")

    val headerInfo = history.mainchainHeaderInfoByHash(mcBlockHash).getOrElse(throw new IllegalStateException("Missed MC Cumulative Hash"))

    headerInfo.cumulativeCommTreeHash
  }

  var provingFileAbsolutePath: String = {
    if (params.certProvingKeyFilePath.isEmpty) {
      throw new IllegalStateException(s"Proving key file name is not set")
    }

    val provingFile: File = new File(params.certProvingKeyFilePath)
    if (!provingFile.canRead) {
      throw new IllegalStateException(s"Proving key file at path ${provingFile.getAbsolutePath} is not exist or can't be read")
      ""
    } else {
      provingFileAbsolutePath = provingFile.getAbsolutePath
      log.debug(s"Found proving key file at location: $provingFileAbsolutePath")
      provingFileAbsolutePath
    }
  }
}

package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils._
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.evm.StateDB
import com.horizen.state.StateView
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.VersionTag
import scorex.util.ScorexLogging
import java.math.BigInteger
import scala.util.Try

class AccountStateView(
  metadataStorageView: AccountStateMetadataStorageView,
  stateDb: StateDB,
  messageProcessors: Seq[MessageProcessor])
  extends StateDbAccountStateView(stateDb, messageProcessors)
    with StateView[SidechainTypes#SCAT]
    with AutoCloseable
    with ScorexLogging {


  def addTopQualityCertificates(refData: MainchainBlockReferenceData): Try[Unit] = Try {
    refData.topQualityCertificate.foreach(cert => {
      log.debug(s"adding top quality cert to state: $cert.")
      updateTopQualityCertificate(cert)
    })
  }

  // out-of-the-box helpers
  override def updateTopQualityCertificate(cert: WithdrawalEpochCertificate): Unit =
    metadataStorageView.updateTopQualityCertificate(cert)

  override def updateFeePaymentInfo(info: AccountBlockFeeInfo): Unit = {
    metadataStorageView.updateFeePaymentInfo(info)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit =
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit =
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)

  override def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit =
    metadataStorageView.updateTransactionReceipts(receipts)

  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] =
    metadataStorageView.getTransactionReceipt(txHash)

  def updateNextBaseFee(baseFee: BigInteger): Unit = metadataStorageView.updateNextBaseFee(baseFee)

  def getNextBaseFee: BigInteger = metadataStorageView.getNextBaseFee

  override def setCeased(): Unit = metadataStorageView.setCeased()

  override def commit(version: VersionTag): Try[Unit] = Try {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash)
    metadataStorageView.commit(version)
  }

  // getters


  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = metadataStorageView.getWithdrawalEpochInfo

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getFeePaymentsInfo(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment] = {
    var blockFeeInfoSeq = metadataStorageView.getFeePayments(withdrawalEpoch)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)
    AccountFeePaymentsUtils.getForgersRewards(blockFeeInfoSeq)
  }



  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot






}

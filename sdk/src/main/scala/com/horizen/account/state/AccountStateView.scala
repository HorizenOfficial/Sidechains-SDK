package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils._
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.evm.StateDB
import com.horizen.sc2sc.CrossChainMessageHash
import com.horizen.state.StateView
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.VersionTag
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger

// this class extends 2 main hierarchies, which are kept separate:
//  - StateView (trait): metadata read/write
//      Implements the methods via metadataStorageView
//  - StateDbAccountStateView (concrete class) : evm stateDb read/write
//      Inherits its methods
class AccountStateView(
    metadataStorageView: AccountStateMetadataStorageView,
    stateDb: StateDB,
    messageProcessors: Seq[MessageProcessor]
) extends StateDbAccountStateView(stateDb, messageProcessors)
      with StateView[SidechainTypes#SCAT]
      with AutoCloseable
      with SparkzLogging {

  def addTopQualityCertificates(refData: MainchainBlockReferenceData, blockId: ModifierId): Unit = {
    refData.topQualityCertificate.foreach(cert => {
      log.debug(s"adding top quality cert to state: $cert.")
      updateTopQualityCertificate(cert, refData.headerHash, blockId)
    })
  }

  // out-of-the-box helpers
  override def updateTopQualityCertificate(cert: WithdrawalEpochCertificate, mainChainHash: Array[Byte], blockId: ModifierId): Unit = {
    metadataStorageView.updateTopQualityCertificate(cert)
    metadataStorageView.updateLastCertificateReferencedEpoch(cert.epochNumber)
    metadataStorageView.updateLastCertificateSidechainBlockIdOpt(blockId)
    metadataStorageView.addTopCertificateMainchainHash(cert.epochNumber, mainChainHash)
  }

  override def getTopCertificateMainchainHash(referencedWithdrawalEpoch: Int): Option[Array[Byte]] =
    metadataStorageView.getTopCertificateMainchainHash(referencedWithdrawalEpoch)

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

  def addSidechainTxCommitmentTreeRootHash(hash: Array[Byte]): Unit = metadataStorageView.addSidechainTxCommitmentTreeRootHash(hash)

  def getNextBaseFee: BigInteger = metadataStorageView.getNextBaseFee

  override def setCeased(): Unit = metadataStorageView.setCeased()

  override def commit(version: VersionTag): Unit = {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash.toBytes)
    metadataStorageView.commit(version)
  }

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = metadataStorageView.getWithdrawalEpochInfo

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getFeePaymentsInfo(
      withdrawalEpoch: Int,
      blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None
  ): Seq[AccountPayment] = {
    var blockFeeInfoSeq = metadataStorageView.getFeePayments(withdrawalEpoch)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)
    AccountFeePaymentsUtils.getForgersRewards(blockFeeInfoSeq)
  }

  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot

  def doesScTxCommitmentTreeRootExist(hash: Array[Byte]): Boolean =
    metadataStorageView.doesScTxCommitmentTreeRootExist(hash)
}

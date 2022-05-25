package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.consensus.ConsensusEpochInfo
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.state.StateView
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.util.ModifierId

import scala.util.Try

class AccountStateView(metadataStorageView: AccountStateMetadataStorageView) extends StateView[SidechainTypes#SCAT, AccountStateView] with AccountStateReader {
  view: AccountStateView =>

  override type NVCT = this.type

  // modifiers
  override def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[AccountStateView] = ???

  override def applyTransaction(tx: SidechainTypes#SCAT): Try[AccountStateView] = ???

  // account modifiers:
  protected def addAccount(address: Array[Byte], account: Account): Try[AccountStateView] = ???

  protected def addBalance(address: Array[Byte], amount: Long): Try[AccountStateView] = ???

  protected def subBalance(address: Array[Byte], amount: Long): Try[AccountStateView] = ???

  protected def updateAccountStorageRoot(address: Array[Byte], root: Array[Byte]): Try[AccountStateView] = ???

  // out-of-the-box helpers
  override protected def addCertificate(cert: WithdrawalEpochCertificate): Try[AccountStateView] = Try {
    metadataStorageView.updateTopQualityCertificate(cert)
    new AccountStateView(metadataStorageView)
  }

  override protected def addWithdrawalRequest(wrb: WithdrawalRequestBox): Try[AccountStateView] = ???

  override protected def delegateStake(fb: ForgerBox): Try[AccountStateView] = ???

  override protected def spendStake(fb: ForgerBox): Try[AccountStateView] = ???

  // note: probably must be "set" than "add". Because we allow it only once per "commit".
  override protected def addFeeInfo(info: BlockFeeInfo): Try[AccountStateView] = Try {
    metadataStorageView.addFeePayment(info)
    new AccountStateView(metadataStorageView)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Try[AccountStateView] = Try {
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)
    new AccountStateView(metadataStorageView)
  }

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Try[AccountStateView] = Try {
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)
    new AccountStateView(metadataStorageView)
  }

  protected def updateAccountStateRoot(accountStateRoot: Array[Byte]): Try[AccountStateView] = Try {
    metadataStorageView.updateAccountStateRoot(accountStateRoot)
    new AccountStateView(metadataStorageView)
  }

  override def setCeased(): Try[AccountStateView] = Try {
    metadataStorageView.setCeased()
    new AccountStateView(metadataStorageView)
  }

  // view controls
  override def savepoint(): Unit = ???

  override def rollbackToSavepoint(): Try[AccountStateView] = ???

  override def commit(version: VersionTag): Try[Unit] = Try {
    // Update StateDB without version, then commit metadataStorageView
    metadataStorageView.commit(version)
  }

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = ???

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)
  }

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch) match {
      case Some(certificate) => certificate.quality
      case None => 0
    }
  }

  // get the record of storage or return WithdrawalEpochInfo(0,0) if state is empty
  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    metadataStorageView.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0))
  }

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochInfo: (ModifierId, ConsensusEpochInfo) = ???

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getBlockFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = {
    metadataStorageView.getFeePayments(withdrawalEpochNumber)
  }

  // account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Long = ???

  override def getAccountStateRoot: Option[Array[Byte]] = metadataStorageView.getAccountStateRoot

}
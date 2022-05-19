package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.state.StateView
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag

import scala.util.Try

class AccountStateView extends StateView[SidechainTypes#SCAT, AccountStateView] with AccountStateReader {
  view: AccountStateView =>
  override type NVCT = this.type

  // modifiers
  override def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[AccountStateView] = ???

  override def applyTransaction(tx: SidechainTypes#SCAT): Try[AccountStateView] = ???

  // account modifiers:
  protected def addAccount(address: Array[Byte], account: Account): Try[AccountStateView] = ???
  protected def addBalance(address: Array[Byte], amount: Long) : Try[AccountStateView] = ???
  protected def subBalance(address: Array[Byte], amount: Long) : Try[AccountStateView] = ???
  protected def updateAccountStorageRoot(address: Array[Byte], root: Array[Byte]) : Try[AccountStateView] = ???

  // out-of-the-box helpers
  override def addCertificate(cert: WithdrawalEpochCertificate): Try[AccountStateView] = ???

  override def addWithdrawalRequest(wrb: WithdrawalRequestBox): Try[AccountStateView] = ???

  override def delegateStake(fb: ForgerBox): Try[AccountStateView] = ???

  override def spendStake(fb: ForgerBox): Try[AccountStateView] = ???

  override def addFeeInfo(info: BlockFeeInfo): Try[AccountStateView] = ???

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Try[AccountStateView] = ???


  override def setCeased(): Try[AccountStateView] = ???

  // view controls
  override def savepoint(): Unit = ???

  override def rollbackToSavepoint(): Try[AccountStateView] = ???

  override def commit(version: VersionTag): Try[Unit] = ???

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = ???

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = ???

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = ???

  // get the record of storage or return WithdrawalEpochInfo(0,0) if state is empty
  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = ???

  override def hasCeased: Boolean = ???

  override def getBlockFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = ???

  // account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Long = ???
}
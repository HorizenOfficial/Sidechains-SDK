package com.horizen.state

import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.utils.AccountBlockFeeInfo
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.VersionTag
import scala.util.Try

trait StateView[TX <: Transaction] extends BaseStateReader {
  def updateTopQualityCertificate(cert: WithdrawalEpochCertificate): Unit
  def updateFeePaymentInfo(info: AccountBlockFeeInfo): Unit
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit
  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit
  def setCeased(): Unit
  def commit(version: VersionTag): Try[Unit]
}

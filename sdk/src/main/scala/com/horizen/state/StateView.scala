package com.horizen.state

import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.utils.AccountBlockFeeInfo
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.VersionTag

import java.math.BigInteger
import scala.util.Try

trait StateView[TX <: Transaction] extends BaseStateReader {
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit
  def updateTopQualityCertificate(cert: WithdrawalEpochCertificate): Unit
  def updateFeePaymentInfo(info: AccountBlockFeeInfo): Unit
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit
  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit
  def updateNextBaseFee(baseFee: BigInteger): Unit
  def setCeased(): Unit
  def commit(version: VersionTag): Unit
}

package io.horizen.state

import io.horizen.account.state.receipt.EthereumReceipt
import io.horizen.account.utils.AccountBlockFeeInfo
import io.horizen.block.WithdrawalEpochCertificate
import io.horizen.consensus.ConsensusEpochNumber
import io.horizen.transaction.Transaction
import io.horizen.utils.WithdrawalEpochInfo
import sparkz.util.ModifierId
import sparkz.core.VersionTag

import java.math.BigInteger

trait StateView[TX <: Transaction] extends BaseStateReader {

  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit
  def updateTopQualityCertificate(cert: WithdrawalEpochCertificate, blockId: ModifierId): Unit
  def updateFeePaymentInfo(info: AccountBlockFeeInfo): Unit
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit
  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit
  def updateNextBaseFee(baseFee: BigInteger): Unit
  def setCeased(): Unit
  def commit(version: VersionTag): Unit
}

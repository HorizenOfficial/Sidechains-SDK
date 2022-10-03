package com.horizen.state

import com.horizen.account.receipt.{EthereumConsensusDataReceipt, EthereumReceipt}
import com.horizen.account.state.{BlockContext, GasPool}
import com.horizen.account.utils.AccountBlockFeeInfo
import com.horizen.block.{MainchainBlockReferenceData,WithdrawalEpochCertificate}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.VersionTag
import scala.util.Try

trait StateView[TX <: Transaction] extends BaseStateReader {
  def applyMainchainBlockReferenceData(
      refData: MainchainBlockReferenceData
  ): Try[Unit]

  def applyTransaction(
      tx: TX,
      txIndex: Int,
      blockGasPool: GasPool,
      blockContext: BlockContext,
      finalizeChanges: Boolean = true
  ): Try[EthereumConsensusDataReceipt]

  def addCertificate(cert: WithdrawalEpochCertificate): Unit
  def addFeeInfo(info: AccountBlockFeeInfo): Unit
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit
  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit
  def setCeased(): Unit

  def commit(version: VersionTag): Try[Unit]
}

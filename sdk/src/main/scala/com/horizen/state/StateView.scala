package com.horizen.state

import com.horizen.account.receipt.{EthereumConsensusDataReceipt, EthereumReceipt}
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag

import java.math.BigInteger
import scala.util.Try

trait StateView[TX <: Transaction] extends BaseStateReader {
  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[Unit]
  def applyTransaction(tx: TX, txIndex: Int, cumGasUsed: BigInteger): Try[EthereumConsensusDataReceipt]

  def addCertificate(cert: WithdrawalEpochCertificate): Unit
  def addFeeInfo(info: BlockFeeInfo): Unit
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit
  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit
  def setCeased(): Unit
  def setBlockNumberForTransactions(blockNumber: Int, listOfTransaction: Seq[scorex.util.ModifierId]): Unit

  def commit(version: VersionTag): Try[Unit]
}

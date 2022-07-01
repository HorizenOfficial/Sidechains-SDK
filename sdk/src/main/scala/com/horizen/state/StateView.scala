package com.horizen.state

import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag

import scala.util.Try

trait StateView[TX <: Transaction] extends BaseStateReader {
  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[Unit]
  def applyTransaction(tx: TX): Try[Unit]

  def addCertificate(cert: WithdrawalEpochCertificate): Unit
  def addFeeInfo(info: BlockFeeInfo): Unit
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit
  def setCeased(): Unit

  def commit(version: VersionTag): Try[Unit]
}

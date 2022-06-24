package com.horizen.state

import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag

import scala.util.Try

trait StateView[TX <: Transaction, SV <: StateView[TX, SV]] extends StateReader {
  view: SV =>

  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[Unit]
  def applyTransaction(tx: TX): Try[SV]

  def addCertificate(cert: WithdrawalEpochCertificate): Try[SV]
  def delegateStake(fb: ForgerBox): Try[SV] // todo
  def spendStake(fb: ForgerBox): Try[SV] // todo
  def addFeeInfo(info: BlockFeeInfo): Try[SV]
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Try[SV]
  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Try[SV]
  def setCeased(): Try[SV]

  def savepoint(): Unit
  def rollbackToSavepoint(): Try[SV]
  def commit(version: VersionTag): Try[Unit] // todo
}

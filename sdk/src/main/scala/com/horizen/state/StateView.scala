package com.horizen.state

import com.horizen.account.state.AccountStateView
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.transaction.Transaction
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag

import scala.util.Try

trait StateView[TX <: Transaction, SV <: StateView[TX, SV]] extends StateReader {
  view: SV =>

  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[SV]
  def applyTransaction(tx: TX): Try[SV]

  protected def addCertificate(cert: WithdrawalEpochCertificate): Try[SV]
  protected def addWithdrawalRequest(wrb: WithdrawalRequestBox): Try[SV]
  protected def delegateStake(fb: ForgerBox): Try[SV] // todo
  protected def spendStake(fb: ForgerBox): Try[SV] // todo
  protected def addFeeInfo(info: BlockFeeInfo): Try[SV]
  protected def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Try[SV]
  protected def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Try[SV]
  protected def setCeased(): Try[SV]

  def savepoint(): Unit
  def rollbackToSavepoint(): Try[SV]
  def commit(version: VersionTag): Try[Unit] // todo
}

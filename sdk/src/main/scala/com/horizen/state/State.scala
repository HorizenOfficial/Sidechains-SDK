package com.horizen.state

import com.horizen.block.{MainchainBlockReferenceData, SidechainBlock, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.transaction.Transaction
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.core.transaction.state.{MinimalState, ModifierValidation, TransactionValidation}

import scala.util.Try

trait StateReader extends scorex.core.transaction.state.StateReader {

  def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox]
  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]
  def certificateTopQuality(referencedWithdrawalEpoch: Int): Long
  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def hasCeased: Boolean

  // todo: consensus related part
  // todo: fee payments related part
}

trait StateView[TX <: Transaction, SV <: StateView[TX, SV]]
  extends StateReader
    with TransactionValidation[TX]
    with ModifierValidation[SidechainBlock] {
  view: SV =>

  // TransactionValidation part, that can be used by NVH for mempool checks:
  // def isValid(tx: TX): Boolean = validate(tx).isSuccess
  //
  //  def filterValid(txs: Seq[TX]): Seq[TX] = txs.filter(isValid)
  //
  //  def validate(tx: TX): Try[Unit]


  // ModifierValidation:
  // def validate(mod: SidechainBlock): Try[Unit]


  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[SV]
  def applyTransaction(tx: TX): Try[SV]

  def addCertificate(cert: WithdrawalEpochCertificate): Try[SV]
  def addWithdrawalRequest(wrb: WithdrawalRequestBox): Try[SV]
  def delegateStake(fb: ForgerBox): Try[SV] // todo
  def spendStake(fb: ForgerBox): Try[SV] // todo
  def addFeeInfo(info: BlockFeeInfo): Try[SV]

  def savepoint(): Unit
  def rollbackToSavepoint(): Try[SV]
  def commit(version: VersionTag): Try[Unit] // todo
}

trait State[TX <: Transaction, SV <: StateView[TX, SV], S <: State[TX, SV, S]]
  extends MinimalState[SidechainBlock, S]
    with StateReader {
  self: S =>
  override type NVCT = this.type

  // MinimalState:
  // def applyModifier(mod: M): Try[MS]
  // def rollbackTo(version: VersionTag): Try[MS]
  // def getReader: StateReader = this

  def getView: SV

  //def getViewInThePast(version: VersionTag): Try[ReadOnlyStateView]
}

package com.horizen

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.Box
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction
import com.horizen.utils.WithdrawalEpochInfo
import scorex.core.{PersistentNodeViewModifier, VersionTag}
import scorex.core.transaction.state.{BoxStateChanges, MinimalState, ModifierValidation, TransactionValidation}

import scala.util.Try

trait BoxMinimalState[P <: Proposition,
    BX <: Box[P],
    BTX <: BoxTransaction[P, BX],
    M <: PersistentNodeViewModifier,
    BMS <: BoxMinimalState[P, BX, BTX, M, BMS]]
  extends MinimalState[M, BMS] with TransactionValidation[BTX] with ModifierValidation[M] {
  self: BMS =>

  def closedBox(boxId: Array[Byte]): Option[BX]

  def changes(mod: M): Try[BoxStateChanges[P, BX]]

  def applyChanges(changes: BoxStateChanges[P, BX],
                   newVersion: VersionTag,
                   withdrawalEpochInfo: WithdrawalEpochInfo,
                   consensusEpoch: ConsensusEpochNumber,
                   withdrawalEpochCertificateOpt: Option[WithdrawalEpochCertificate]): Try[BMS]

  override def validate(mod: M): Try[Unit]

  /**
    * A transaction is valid against a state if:
    * - boxes a transaction is opening are stored in the state as closed
    * - sum of values of closed boxes = sum of values of open boxes - fee
    * - all the signatures for open boxes are valid(against all the txs bytes except of sigs)
    *
    * - fee >= 0
    *
    * specific semantic rules are applied
    *
    * @param tx - transaction to check against the state
    * @return
    */
  override def validate(tx: BTX): Try[Unit] = ??? /*{
    val statefulValid = {
      val boxesSumTry = tx.unlockers.foldLeft[Try[Long]](Success(0L)) { case (partialRes, unlocker) =>
        partialRes.flatMap { partialSum =>
          closedBox(unlocker.closedBoxId) match {
            case Some(box) =>
              unlocker.boxKey.isValid(box.proposition, tx.messageToSign) match {
                case true => Success(partialSum + box.value)
                case false => Failure(new Exception("Incorrect unlocker"))
              }
            case None => Failure(new Exception(s"Box for unlocker $unlocker is not in the state"))
          }
        }
      }

      boxesSumTry flatMap { openSum =>
        tx.newBoxes.map(_.value).sum == openSum - tx.fee match {
          case true => Success[Unit](Unit)
          case false => Failure(new Exception("Negative fee"))
        }
      }
    }
    statefulValid.flatMap(_ => semanticValidity(tx))
  }*/

  def semanticValidity(tx: BTX): Try[Unit]
}


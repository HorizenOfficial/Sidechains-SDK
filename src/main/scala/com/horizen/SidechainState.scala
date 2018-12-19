package com.horizen

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.state.ApplicationState
import com.horizen.transaction.{BoxTransaction, MC2SCAggregatedTransaction, WithdrawalRequestTransaction}
import scorex.core.{VersionTag, idToVersion}
import scorex.mid.state.BoxMinimalState
import scorex.core.block.Block
import scorex.core.transaction.state.{BoxStateChangeOperation, BoxStateChanges, Insertion, Removal}

import scala.util.{Failure, Success, Try}

class LSMStore

case class SidechainState(store: LSMStore, override val version: VersionTag, applicationState: ApplicationState) extends
    BoxMinimalState[Proposition,
                    Box[Proposition],
                    BoxTransaction[Proposition, Box[Proposition]],
                    SidechainBlock,
                    SidechainState]{

  //require(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version) == version,
  //  s"${encoder.encode(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version))} != ${encoder.encode(version)}")

  override type NVCT = SidechainState
  //type HPMOD = HybridBlock


  // Note: emit tx.semanticValidity for each tx
  override def semanticValidity(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

  // get closed box from State storage
  override def closedBox(boxId: Array[Byte]): Option[Box[Proposition]] = ???

  // get boxes for given proposition from state storage
  override def boxesOf(proposition: Proposition): Seq[Box[Proposition]] = ???

  // Note: aggregate New boxes and spent boxes for Block
  override def changes(mod: SidechainBlock)
  : Try[BoxStateChanges[Proposition, Box[Proposition]]] = {
    SidechainState.changes(mod)
  }

  // Validate block itself, then validate transactions through validateAgainstModifier(tx, mod)
  override def validate(mod: SidechainBlock): Try[Unit] = ???

  // Note: Transactions validation in a context of inclusion in or exclusion from Mempool
  // Note 2: BT and FT is not included into memory pool and have another check rule.
  // TO DO: (almost the same as in NodeViewHolder)
  // 1) check if all unlocker are related to EXISTING CLOSED boxes (B) and able to open them
  // 2) check if for each B, that is instance of CoinBox interface, that total sum is equal to new CoinBox'es sum minus tx.fee
  // 3) if it's a Sidechain custom Transaction (not known) -> emit applicationState.validate(tx)
  // TO DO: put validateAgainstModifier logic inside validate(mod)

  override def validate(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

  // NOTE: mod is only for internal usage: e.g. for Backward and Forward transactions.
  def validateAgainstModifier(tx: BoxTransaction[Proposition, Box[Proposition]],
               mod: SidechainBlock): Try[Unit] = {
    tx match {
      case t: MC2SCAggregatedTransaction => validateMC2SCAggregatedTx(t, mod)
      case t: WithdrawalRequestTransaction => validateWithdrawalRequestTx(t)
      // other SDK known objects with specific validation processing
      // ...
      case _ => Try { // RegularTransactions and custom sidechain transactions
        validate(tx)
      }
    }
  }

  def validateMC2SCAggregatedTx(tx: MC2SCAggregatedTransaction,
                        mod: SidechainBlock
                       ): Try[Unit] = Try {
    // 1) check that MC2SCAggregatedTransaction (forward transaction) contains all sidechain related FT
    // 2) check that transaction is valid
  }

  def validateWithdrawalRequestTx(tx: WithdrawalRequestTransaction): Try[Unit] = Try {
    // validate unlockers to be sure that we can spent proper boxes.
    // no new boxes must be created
  }

  override def applyModifier(mod: SidechainBlock): Try[SidechainState] = {
    validate(mod) flatMap { _ =>
      changes(mod).flatMap(cs => {
        applyChanges(cs, idToVersion(mod.id)) // check applyChanges implementation
      })
    }
  }

  // apply global changes and deleagate SDK unknown part to Sidechain.applyChanges(...)
  // 1) get boxes ids to remove, and boxes to append from "changes"
  // 2) call applicationState.applyChanges(changes):
  //    if ok -> return updated SDKState -> update SDKState store
  //    if fail -> rollback applicationState
  // 3) ensure everithing applied OK and return new SDKState. If not -> return error
  override def applyChanges(changes: BoxStateChanges[Proposition, Box[Proposition]], newVersion: VersionTag): Try[SidechainState] = ???

  override def maxRollbackDepth: Int = ??? //store.keepVersions

  override def rollbackTo(version: VersionTag): Try[SidechainState] = ???

}


object SidechainState {
  def semanticValidity(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

  // TO DO: implement for real block. Now it's just an example.
  // return the list of what boxes we need to remove and what to append
  def changes(mod: SidechainBlock)
    : Try[BoxStateChanges[Proposition, Box[Proposition]]] = {

    val transactions: Seq[BoxTransaction[Proposition]] = Seq()

    Try {
      val initial = (Seq(): Seq[Array[Byte]], Seq(): Seq[Box[Proposition]], 0L)

      // calculate list of ID of unlokers' boxes -> toRemove
      // calculate list of new boxes -> toAppend
      // calculate the rewards for Miner/Forger -> create another regular tx OR Forger need to add his Reward during block creation

      @SuppressWarnings(Array("org.wartremover.warts.Product","org.wartremover.warts.Serializable"))
      val ops: Seq[BoxStateChangeOperation[Proposition, Box[Proposition]]] =
        initial._1.map(id => Removal[Proposition, Box[Proposition]](id)) ++
          initial._2.map(b => Insertion[Proposition, Box[Proposition]](b))
      BoxStateChanges[Proposition, Box[Proposition]](ops)

      // Q: Do we need to call some static method of ApplicationState?
      // A: Probably yes. To remove some out of date boxes, like VoretBallotRight box for previous voting epoch.
      // Note: we need to implement a lot of limitation for changes from ApplicationState (only deletion, only non coin realted boxes, etc.)
    }
  }
}

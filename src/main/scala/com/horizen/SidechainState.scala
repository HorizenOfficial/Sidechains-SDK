package com.horizen

import java.util

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.state.ApplicationState
import com.horizen.transaction.{BoxTransaction, MC2SCAggregatedTransaction, WithdrawalRequestTransaction}
import scorex.core.{VersionTag, idToVersion}
import scorex.core.transaction.state.{BoxStateChangeOperation, BoxStateChanges, Insertion, Removal}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import com.horizen.node.NodeState


class LSMStore

case class SidechainState(store: LSMStore, override val version: VersionTag, applicationState: ApplicationState) extends
    BoxMinimalState[Proposition,
                    Box[Proposition],
                    BoxTransaction[Proposition, Box[Proposition]],
                    SidechainBlock,
                    SidechainState] with NodeState {

  //require(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version) == version,
  //  s"${encoder.encode(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version))} != ${encoder.encode(version)}")

  override type NVCT = SidechainState
  //type HPMOD = SidechainBlock


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
  // In the block validation we need to verify that for every MC block referenced there is a corresponding MC2SC transaction and verify merkle roots in transaction and block is equal
  // and moreover verify that every mc2sc transaction has a corresponding mainchain block reference.
  override def validate(mod: SidechainBlock): Try[Unit] = ???

  // Note: Transactions validation in a context of inclusion in or exclusion from Mempool
  // Note 2: BT and FT is not included into memory pool and have another check rule.
  // TO DO: (almost the same as in NodeViewHolder)
  // 1) check if all unlocker are related to EXISTING CLOSED boxes (B) and able to open them
  // 2) check if for each B, that is instance of CoinBox interface, that total sum is equal to new CoinBox'es sum minus tx.fee
  // 3) if it's a Sidechain custom Transaction (not known) -> emit applicationState.validate(tx)
  // TO DO: put validateAgainstModifier logic inside validate(mod)

  // TO DO: in SidechainState(BoxMinimalState) in validate(TX) method we need to introduce special processing for MC2SCAggregatedTransaction
  override def validate(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

  // NOTE: mod is only for internal usage: e.g. for Backward and Forward transactions.
  def validateAgainstModifier(tx: BoxTransaction[Proposition, Box[Proposition]],
               mod: SidechainBlock): Try[Unit] = {
    tx match {
      //case t: MC2SCAggregatedTransaction => validateMC2SCAggregatedTx(t, mod)
      //case t: WithdrawalRequestTransaction => validateWithdrawalRequestTx(t)
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

  override def getClosedBoxes(boxIdsToExclude: util.List[Array[Byte]]): util.List[Box[_ <: Proposition]] = ???

  override def getClosedBoxesOfType(`type`: Class[_ <: Box[_ <: Proposition]], boxIdsToExclude: util.List[Array[Byte]]): util.List[Box[_ <: Proposition]] = ???

}

object SidechainState {
  def semanticValidity(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

  // TO DO: implement for real block. Now it's just an example.
  // return the list of what boxes we need to remove and what to append
  def changes(mod: SidechainBlock) : Try[BoxStateChanges[Proposition, Box[Proposition]]] = Try {
    val initial = (Seq(): Seq[Array[Byte]], Seq(): Seq[Box[Proposition]], 0L)

    val (toRemove: Seq[Array[Byte]], toAdd: Seq[Box[Proposition]], reward) =
      mod.transactions.foldLeft(initial){ case ((sr, sa, f), tx) =>
        (sr ++ tx.unlockers().asScala.map(_.closedBoxId()), sa ++ tx.newBoxes().asScala, f + tx.fee())
      }

    // calculate list of ID of unlokers' boxes -> toRemove
    // calculate list of new boxes -> toAppend
    // calculate the rewards for Miner/Forger -> create another regular tx OR Forger need to add his Reward during block creation
    @SuppressWarnings(Array("org.wartremover.warts.Product","org.wartremover.warts.Serializable"))
    val ops: Seq[BoxStateChangeOperation[Proposition, Box[Proposition]]] =
      toRemove.map(id => Removal[Proposition, Box[Proposition]](scorex.crypto.authds.ADKey(id))) ++
      toAdd.map(b => Insertion[Proposition, Box[Proposition]](b))

    BoxStateChanges[Proposition, Box[Proposition]](ops)

    // Q: Do we need to call some static method of ApplicationState?
    // A: Probably yes. To remove some out of date boxes, like VoretBallotRight box for previous voting epoch.
    // Note: we need to implement a lot of limitation for changes from ApplicationState (only deletion, only non coin realted boxes, etc.)
  }
}

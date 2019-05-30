package com.horizen

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.state.ApplicationState
import com.horizen.storage.SidechainStateStorage
import com.horizen.transaction.{BoxTransaction, MC2SCAggregatedTransaction, WithdrawalRequestTransaction}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.{VersionTag, idToVersion}
import scorex.core.transaction.state.{BoxStateChangeOperation, BoxStateChanges, Insertion, Removal}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._


case class SidechainState(store: SidechainStateStorage, override val version: VersionTag, applicationState: ApplicationState)
  extends
    BoxMinimalState[Proposition,
                    Box[_ <: Proposition],
                    BoxTransaction[Proposition, Box[Proposition]],
                    SidechainBlock,
                    SidechainState]{

  //require(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version) == version,
  //  s"${encoder.encode(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version))} != ${encoder.encode(version)}")

  override type NVCT = SidechainState
  //type HPMOD = SidechainBlock


  // Note: emit tx.semanticValidity for each tx
  override def semanticValidity(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

  // get closed box from State storage
  override def closedBox(boxId: Array[Byte]): Option[Box[_ <: Proposition]] = {
    store.get(boxId)
  }

  // get boxes for given proposition from state storage
  override def boxesOf(proposition: Proposition): Seq[Box[Proposition]] = ???

  // Note: aggregate New boxes and spent boxes for Block
  override def changes(mod: SidechainBlock) : Try[BoxStateChanges[Proposition, Box[_ <: Proposition]]] = {
    SidechainState.changes(mod)
  }

  // Validate block itself: version and semanticValidity for block
  //TODO add call of applicationState.validate(Block)
  //TODO see validate method in Hybrid (tx validation also)
  override def validate(mod: SidechainBlock): Try[Unit] = ???

  // Note: Transactions validation in a context of inclusion in or exclusion from Mempool
  // Note 2: BT and FT is not included into memory pool and have another check rule.
  // TO DO: (almost the same as in NodeViewHolder)
  // 1) check if all unlocker are related to EXISTING CLOSED boxes (B) and able to open them
  // 2) check if for each B, that is instance of CoinBox interface, that total sum is equal to new CoinBox'es sum minus tx.fee
  // 3) if it's a Sidechain custom Transaction (not known) -> emit applicationState.validate(tx)
  // TO DO: put validateAgainstModifier logic inside validate(mod)

  // TO DO: in SidechainState(BoxMinimalState) in validate(TX) method we need to introduce special processing for MC2SCAggregatedTransaction
  //TODO check logic in Hybrid.BoxMinimalState.validate
  //TODO TBD
  override def validate(tx: BoxTransaction[Proposition, Box[Proposition]]): Try[Unit] = ???

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

  override def maxRollbackDepth: Int = {
    store.rollbackVersions.size
  }

  override def rollbackTo(to: VersionTag): Try[SidechainState] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    val version = BytesUtils.fromHexString(to)
    store.rollback(new ByteArrayWrapper(version)).get
    applicationState.onRollback(version)
    this
  }

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

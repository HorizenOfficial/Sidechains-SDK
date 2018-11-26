import scorex.core.{VersionTag, idToVersion}
import scorex.mid.state.BoxMinimalState
import scorex.core.block.Block
import scorex.core.transaction.state.{BoxStateChangeOperation, BoxStateChanges, Insertion, Removal}

import scala.util.{Failure, Success, Try}

class LSMStore

case class SDKState(store: LSMStore, override val version: VersionTag, sidechainState: SidechainState) extends
    BoxMinimalState[ProofOfKnowledgeProposition[Secret],
                    Box[ProofOfKnowledgeProposition[Secret]],
                    BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]],
                    Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]],
                    SDKState]{

  //require(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version) == version,
  //  s"${encoder.encode(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version))} != ${encoder.encode(version)}")

  override type NVCT = SDKState
  //type HPMOD = HybridBlock


  // Note: emit tx.semanticValidity for each tx
  override def semanticValidity(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]): Try[Unit] = ???

  // get closed box from State storage
  override def closedBox(boxId: Array[Byte]): Option[Box[ProofOfKnowledgeProposition[Secret]]] = ???

  // get boxes for given proposition from state storage
  override def boxesOf(proposition: ProofOfKnowledgeProposition[Secret]): Seq[Box[ProofOfKnowledgeProposition[Secret]]] = ???

  // Note: aggregate New boxes and spent boxes for Block
  override def changes(mod: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]])
  : Try[BoxStateChanges[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] = {
    SDKState.changes(mod)
  }

  // Validate block itself, then validate transactions through validateAgainstModifier(tx, mod)
  override def validate(mod: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[Unit] = ???

  // Note: Transactions validation in a context of inclusion in or exclusion from Mempool
  // Note 2: BT and FT is not included into memory pool and have another check rule.
  // TO DO: (almost the same as in NodeViewHolder)
  // 1) check if all unlocker are related to EXISTING CLOSED boxes (B) and able to open them
  // 2) check if for each B, that is instance of CoinBox interface, that total sum is equal to new CoinBox'es sum minus tx.fee
  // 3) if it's a Sidechain custom Transaction (not known) -> emit sidechainState.validate(tx)
  override def validate(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]): Try[Unit] = ???

  // NOTE: mod is only for internal usage: e.g. for Backward and Forward transactions.
  def validateAgainstModifier(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]],
               mod: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[Unit] = {
    tx match {
      case t: ForwardTransaction => validateForwardTx(t, mod)
      case t: BackwardTransaction => validateBackwardTx(t)
      // other SDK known objects with specific validation processing
      // ...
      case _ => Try { // RegularTransactions and custom sidechain transactions
        validate(tx)
      }
    }
  }

  def validateForwardTx(tx: ForwardTransaction,
                        mod: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]
                       ): Try[Unit] = Try {
    // to do check FT against current Block
  }

  def validateBackwardTx(tx: BackwardTransaction): Try[Unit] = Try {
    // validate unlockers to be sure that we can spent proper boxes.
    // no new boxes must be created
  }

  override def applyModifier(mod: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[SDKState] = {
    validate(mod) flatMap { _ =>
      changes(mod).flatMap(cs => {
        applyChanges(cs, idToVersion(mod.id)) // check applyChanges implementation
      })
    }
  }

  // apply global changes and deleagate SDK unknown part to Sidechain.applyChanges(...)
  // 1) get boxes ids to remove, and boxes to append from "changes"
  // 2) call sidechainState.applyChanges(changes):
  //    if ok -> return updated SDKState -> update SDKState store
  //    if fail -> rollback sidechainState
  // 3) ensure everithing applied OK and return new SDKState. If not -> return error
  override def applyChanges(changes: BoxStateChanges[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]], newVersion: VersionTag): Try[SDKState] = ???

  override def maxRollbackDepth: Int = ??? //store.keepVersions

  override def rollbackTo(version: VersionTag): Try[SDKState] = ???

}


object SDKState {
  def semanticValidity(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]): Try[Unit] = ???

  // TO DO: implement for real block. Now it's just an example.
  // return the list of what boxes we need to remove and what to append
  def changes(mod: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]])
    : Try[BoxStateChanges[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] = {

    val transactions: Seq[BoxTransaction[ProofOfKnowledgeProposition[Secret]]] = Seq()

    Try {
      val initial = (Seq(): Seq[Array[Byte]], Seq(): Seq[Box[ProofOfKnowledgeProposition[Secret]]], 0L)

      // calculate list of ID of unlokers' boxes -> toRemove
      // calculate list of new boxes -> toAppend
      // calculate the rewards for Miner/Forger -> create another regular tx OR Forger need to add his Reward during block creation

      @SuppressWarnings(Array("org.wartremover.warts.Product","org.wartremover.warts.Serializable"))
      val ops: Seq[BoxStateChangeOperation[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] =
        initial._1.map(id => Removal[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]](id)) ++
          initial._2.map(b => Insertion[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]](b))
      BoxStateChanges[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]](ops)

      // Q: Do we need to call some static method of SidechainState?
      // A: Probably yes. To remove some out of date boxes, like VoretBallotRight box for previous voting epoch.
      // Note: we need to implement a lot of limitation for changes from SidechainState (only deletion, only non coin realted boxes, etc.)
    }
  }
}

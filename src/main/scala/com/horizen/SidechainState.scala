package com.horizen

import java.util.Optional

import com.horizen.block.SidechainBlock
import com.horizen.box.{Box, CoinsBox}
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.state.{ApplicationState, SidechainStateReader}
import com.horizen.storage.SidechainStateStorage
import com.horizen.transaction.{BoxTransaction, MC2SCAggregatedTransaction, WithdrawalRequestTransaction}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.{VersionTag, idToVersion, bytesToVersion}
import scorex.core.transaction.state.{BoxStateChangeOperation, BoxStateChanges, Insertion, Removal}
import scorex.util.ScorexLogging

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._


case class SidechainState(store: SidechainStateStorage, override val version: VersionTag, applicationState: ApplicationState)
  extends
    BoxMinimalState[SidechainTypes#P,
                    SidechainTypes#B,
                    SidechainTypes#BT,
                    SidechainBlock,
                    SidechainState]
  with SidechainTypes
  with SidechainStateReader
  with ScorexLogging
{

  //require(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version) == version,
  //  s"${encoder.encode(store.lastVersionID.map(w => bytesToVersion(w.data)).getOrElse(version))} != ${encoder.encode(version)}")

  override type NVCT = SidechainState
  //type HPMOD = SidechainBlock

  // Note: emit tx.semanticValidity for each tx
  override def semanticValidity(tx: BT): Try[Unit] = Try {
    if (!tx.semanticValidity())
      throw new Exception("Transaction is semanticaly invalid.")
  }

  // get closed box from State storage
  override def closedBox(boxId: Array[Byte]): Option[B] = {
    store.get(boxId)
  }

  override def getClosedBox(boxId: Array[Byte]): Optional[B] = {
    Optional.ofNullable(closedBox(boxId).orNull)
  }
  // get boxes for given proposition from state storage
  override def boxesOf(proposition: P): Seq[B] = ???

  // Note: aggregate New boxes and spent boxes for Block
  override def changes(mod: SidechainBlock) : Try[BoxStateChanges[P, B]] = {
    SidechainState.changes(mod)
  }

  // Validate block itself: version and semanticValidity for block
  //TODO add call of applicationState.validate(Block)
  //TODO see validate method in Hybrid (tx validation also)
  override def validate(mod: SidechainBlock): Try[Unit] = Try {
    require(mod.parentId == version, s"Incorrect state version!: ${mod.parentId} found, " +
      s"${version} expected")
    //TODO (Alberto) Do we really need to check semanticValidity for block (and transaction) here???
    //mod.semanticValidity()
    mod.transactions.foreach(tx => validate(tx).get)
    //TODO Try as result of validate?
    if (!applicationState.validate(this, mod))
      throw new Exception("Exception was thrown by ApplicationState validation.")
  }

  // Note: Transactions validation in a context of inclusion in or exclusion from Mempool
  // Note 2: BT and FT is not included into memory pool and have another check rule.
  // TO DO: (almost the same as in NodeViewHolder)
  // 1) check if all unlocker are related to EXISTING CLOSED boxes (B) and able to open them
  // 2) check if for each B, that is instance of CoinBox interface, that total sum is equal to new CoinBox'es sum minus tx.fee
  // 3) if it's a Sidechain custom Transaction (not known) -> emit applicationState.validate(tx)
  // TO DO: put validateAgainstModifier logic inside validate(mod)

  // TO DO: in SidechainState(BoxMinimalState) in validate(TX) method we need to introduce special processing for MC2SCAggregatedTransaction
  // TO DO check logic in Hybrid.BoxMinimalState.validate
  // TO DO TBD
  override def validate(tx: BT): Try[Unit] = Try {
    var closedCoinsBoxesAmount : Long = 0L
    var newCoinsBoxesAmount : Long = 0L

    if (!tx.isInstanceOf[MC2SCAggregatedTransaction]) {

      for (u <- tx.unlockers().asScala) {
        closedBox(u.closedBoxId()) match {
          case Some(box) => {
            val boxKey = u.boxKey()
            if (!boxKey.isValid(box.proposition(), tx.messageToSign()))
              throw new Exception("Signature is invalid.")
            if (box.isInstanceOf[CoinsBox[_ <: Proposition]])
              closedCoinsBoxesAmount += box.value()
          }
          case None => throw new Exception(s"Box ${u.closedBoxId()} is not found in state")
        }
      }

      newCoinsBoxesAmount = tx.newBoxes().asScala.filter(_.isInstanceOf[CoinsBox[_ <: Proposition]]).map(_.value()).sum

      if (closedCoinsBoxesAmount + tx.fee() != newCoinsBoxesAmount)
        throw new Exception("Amounts sum of CoinsBoxes is incorrect.");

    }

    semanticValidity(tx);
  }

  override def applyModifier(mod: SidechainBlock): Try[SidechainState] = {
    validate(mod).flatMap { _ =>
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
  override def applyChanges(changes: BoxStateChanges[P, B], newVersion: VersionTag): Try[SidechainState] = Try {
    val version = BytesUtils.fromHexString(newVersion)
//    val boxesToAppend =
//    val boxIdsToRemove =
    applicationState.onApplyChanges(this, version,
      changes.toAppend.map(_.box).asJava,
      changes.toRemove.map(_.boxId.array).asJava) match {
      case Success(appState) =>
        SidechainState(store.update(new ByteArrayWrapper(version), changes.toAppend.map(_.box).toSet,
                                    changes.toRemove.map(_.boxId.array).toSet).get,
                       newVersion, applicationState)
      case Failure(exception) => throw exception
    }
  }.recoverWith{case exception =>
    log.error("Exception was thrown during applyChanges.", exception)
    Failure(exception)
  }

  override def maxRollbackDepth: Int = {
    store.rollbackVersions.size
  }

  override def rollbackTo(to: VersionTag): Try[SidechainState] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    val version = BytesUtils.fromHexString(to)
    applicationState.onRollback(version) match {
      case Success(appState) => SidechainState(store.rollback(new ByteArrayWrapper(version)).get, to, appState)
      case Failure(exception) => throw exception
    }
  }.recoverWith{case exception =>
    log.error("Exception was thrown during rollback.", exception)
    Failure(exception)
  }
}

object SidechainState
  extends SidechainTypes
{

  //TODO should it be the same as in class?
  def semanticValidity(tx: SidechainTypes#BT): Try[Unit] = ???

  // TO DO: implement for real block. Now it's just an example.
  // return the list of what boxes we need to remove and what to append
  def changes(mod: SidechainBlock) : Try[BoxStateChanges[P, B]] = Try {
    val initial = (Seq(): Seq[Array[Byte]], Seq(): Seq[B], 0L)

    val (toRemove: Seq[Array[Byte]], toAdd: Seq[B], reward) =
      mod.transactions.foldLeft(initial){ case ((sr, sa, f), tx) =>
        (sr ++ tx.unlockers().asScala.map(_.closedBoxId()), sa ++ tx.newBoxes().asScala, f + tx.fee())
      }

    // calculate list of ID of unlokers' boxes -> toRemove
    // calculate list of new boxes -> toAppend
    // calculate the rewards for Miner/Forger -> create another regular tx OR Forger need to add his Reward during block creation
    @SuppressWarnings(Array("org.wartremover.warts.Product","org.wartremover.warts.Serializable"))
    val ops: Seq[BoxStateChangeOperation[P, B]] =
    toRemove.map(id => Removal[P, B](scorex.crypto.authds.ADKey(id))) ++
      toAdd.map(b => Insertion[P, B](b))

    BoxStateChanges[P, B](ops)

    // Q: Do we need to call some static method of ApplicationState?
    // A: Probably yes. To remove some out of date boxes, like VoretBallotRight box for previous voting epoch.
    // Note: we need to implement a lot of limitation for changes from ApplicationState (only deletion, only non coin realted boxes, etc.)
  }

  def readOrGenerate(store: SidechainStateStorage, applicationState: ApplicationState) : Try[SidechainState] = Try {
    store.lastVersionId match {
      case Some(version) => SidechainState(store, bytesToVersion(version.data), applicationState)
      //TODO Initial version for empty storage???
      case None => SidechainState(store, null, applicationState)
    }
  }
}

package com.horizen

import java.io.File
import java.util
import java.util.{Optional => JOptional}

import com.horizen.block.{SidechainBlock, WithdrawalEpochCertificate}
import com.horizen.box.{Box, CoinsBox, ForgerBox, WithdrawalRequestBox}
import com.horizen.consensus._
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.node.NodeState
import com.horizen.params.NetworkParams
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.state.ApplicationState
import com.horizen.storage.SidechainStateStorage
import com.horizen.transaction.MC2SCAggregatedTransaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, WithdrawalEpochInfo, WithdrawalEpochUtils}
import scorex.core._
import scorex.core.transaction.state.{BoxStateChangeOperation, BoxStateChanges, Insertion, Removal}
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}


class SidechainState private[horizen] (stateStorage: SidechainStateStorage, val params: NetworkParams, override val version: VersionTag, applicationState: ApplicationState)
  extends
    BoxMinimalState[SidechainTypes#SCP,
                    SidechainTypes#SCB,
                    SidechainTypes#SCBT,
                    SidechainBlock,
                    SidechainState]
    with SidechainTypes
    with NodeState
    with ScorexLogging
    with TimeToEpochSlotConverter
{
  require({
    stateStorage.lastVersionId match {
      case Some(storageVersion) => storageVersion.data.sameElements(versionToBytes(version))
      case None => true
    }
  },
    s"Specified version is invalid. ${stateStorage.lastVersionId.map(w => bytesToVersion(w.data)).getOrElse(version)} != ${version}")

  override type NVCT = SidechainState

  lazy val verificationKeyFullFilePath: String = {
    if (params.verificationKeyFilePath.equalsIgnoreCase("")) {
      throw new IllegalStateException(s"Verification key file name is not set")
    }

    val verificationFile: File = new File(params.provingKeyFilePath)
    if (!verificationFile.canRead) {
      throw new IllegalStateException(s"Verification key file at path ${verificationFile.getAbsolutePath} is not exist or can't be read")
    }
    else {
      log.info(s"Verification key file at location: ${verificationFile.getAbsolutePath}")
      verificationFile.getAbsolutePath
    }
  }

  // Note: emit tx.semanticValidity for each tx
  override def semanticValidity(tx: SidechainTypes#SCBT): Try[Unit] = Try {
    if (!tx.semanticValidity())
      throw new Exception("Transaction is semantically invalid.")
  }

  // get closed box from State storage
  override def closedBox(boxId: Array[Byte]): Option[SidechainTypes#SCB] = {
    stateStorage.getBox(boxId)
  }

  override def getClosedBox(boxId: Array[Byte]): JOptional[Box[_ <: Proposition]] = {
    closedBox(boxId) match {
      case Some(box) => JOptional.of(box)
      case None => JOptional.empty()
    }
  }

  def withdrawalRequests(epoch: Int): Seq[WithdrawalRequestBox] = {
    stateStorage.getWithdrawalRequests(epoch)
  }

  def getUnprocessedWithdrawalRequests(epoch: Integer): Option[Seq[WithdrawalRequestBox]] = {
    stateStorage.getUnprocessedWithdrawalRequests(epoch)
  }

  def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0,0))
  }

  // Note: aggregate New boxes and spent boxes for Block
  override def changes(mod: SidechainBlock) : Try[BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB]] = {
    SidechainState.changes(mod)
  }

  // Validate block itself: version and semanticValidity for block
  override def validate(mod: SidechainBlock): Try[Unit] = Try {
    require(versionToBytes(version).sameElements(idToBytes(mod.parentId)), s"Incorrect state version!: ${mod.parentId} found, " +
      s"${version} expected")
    mod.transactions.foreach(tx => validate(tx).get)

    //Check content of the backward transfer certificate if it exists
    //Currently sidechain block can contain 0 or 1 certificate (this is checked in validation of the block in history)
    //so flatMap returns collection with only 1 certificate if it exists or empty collection if certificate does not exist in block
    for (certificate <- mod.withdrawalEpochCertificateOpt) {
      getUnprocessedWithdrawalRequests(certificate.epochNumber) match {
        case Some(withdrawalRequests) => {
          if (withdrawalRequests.size != certificate.backwardTransferOutputs.size)
            throw new Exception("Block contains backward transfer certificate for epoch %d, but list of it's outputs and list of withdrawal requests for this epoch are different.".format(certificate.epochNumber))

          for (o <- certificate.backwardTransferOutputs) {
            if (!withdrawalRequests.exists(r => {
              util.Arrays.equals(r.proposition().bytes(), o.pubKeyHash) &&
                r.value().equals(o.amount)
            })) {
            throw new Exception("Block contains backward transfer certificate for epoch %d, but list of it's outputs and list of withdrawal requests for this epoch are different.".format(certificate.epochNumber))
            }
          }

          val previousEndEpochBlockHash: Array[Byte] =
            stateStorage
              .getLastCertificateEndEpochMcBlockHashOpt
              .getOrElse({
                require(certificate.epochNumber == 0, "Certificate epoch number > 0, but end previous epoch mc block hash was not found.")
                params.parentHashOfGenesisMainchainBlock
              })

          log.info(s"Verify backward transfer certificate with parameters: withdrawalRequests = ${withdrawalRequests.foreach(_.toString)}, certificate.endEpochBlockHash = ${BytesUtils.toHexString(certificate.endEpochBlockHash)}, previousEndEpochBlockHash = ${BytesUtils.toHexString(previousEndEpochBlockHash)}, certificate.quality = ${certificate.quality}, certificate.proof=${BytesUtils.toHexString(certificate.proof)}")

          val proofInCertificateIsValid = CryptoLibProvider.sigProofThresholdCircuitFunctions.verifyProof(
            withdrawalRequests.asJava,
            BytesUtils.reverseBytes(certificate.endEpochBlockHash),
            BytesUtils.reverseBytes(previousEndEpochBlockHash),
            certificate.quality,
            certificate.proof,
            params.calculatedSysDataConstant,
            verificationKeyFullFilePath)

          if (proofInCertificateIsValid) {
            log.info("Block contains successfully verified backward transfer certificate for epoch %d")
          }
          else {
            throw new Exception("Block contains backward transfer certificate for epoch %d, but proof is not correct.".format(certificate.epochNumber))
          }
        }

        case None =>
          throw new Exception("Block contains backward transfer certificate for epoch %d, but list of withdrawal certificates for this epoch is empty.".format(certificate.epochNumber))
      }
    }

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
  override def validate(tx: SidechainTypes#SCBT): Try[Unit] = Try {
    var closedCoinsBoxesAmount : Long = 0L
    var newCoinsBoxesAmount : Long = 0L

    if (!tx.isInstanceOf[MC2SCAggregatedTransaction]) {

      for (u <- tx.unlockers().asScala) {
        closedBox(u.closedBoxId()) match {
          case Some(box) => {
            val boxKey = u.boxKey()
            if (!boxKey.isValid(box.proposition(), tx.messageToSign()))
              throw new Exception("Box unlocking proof is invalid.")
            if (box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]])
              closedCoinsBoxesAmount += box.value()
          }
          case None => throw new Exception(s"Box ${u.closedBoxId()} is not found in state")
        }
      }

      newCoinsBoxesAmount = tx.newBoxes().asScala
        .filter(box => box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]] || box.isInstanceOf[WithdrawalRequestBox])
        .map(_.value()).sum

      if (closedCoinsBoxesAmount != newCoinsBoxesAmount + tx.fee())
        throw new Exception("Amounts sum of CoinsBoxes is incorrect. " +
          s"ClosedBox amount - $closedCoinsBoxesAmount, NewBoxesAmount - $newCoinsBoxesAmount, Fee - ${tx.fee()}")

    }

    semanticValidity(tx).get
    if(!applicationState.validate(this, tx))
      throw new Exception(s"ApplicationState transaction ${tx.id} validation failed.")
  }

  override def applyModifier(mod: SidechainBlock): Try[SidechainState] = {
    validate(mod).flatMap { _ =>
      changes(mod).flatMap(cs => {
        applyChanges(
          cs,
          idToVersion(mod.id),
          WithdrawalEpochUtils.getWithdrawalEpochInfo(mod, stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0,0)), params),
          timeStampToEpochNumber(mod.timestamp),
          mod.withdrawalEpochCertificateOpt
        )
      })
    }
  }

  // apply global changes and delegate SDK unknown part to Sidechain.applyChanges(...)
  // 1) get boxes ids to remove, and boxes to append from "changes"
  // 2) call applicationState.applyChanges(changes):
  //    if ok -> return updated SDKState -> update SidechainState store
  //    if fail -> rollback applicationState
  // 3) ensure everything applied OK and return new SidechainState. If not -> return error
  override def applyChanges(changes: BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB],
                            newVersion: VersionTag,
                            withdrawalEpochInfo: WithdrawalEpochInfo,
                            consensusEpoch: ConsensusEpochNumber, withdrawalEpochCertificateOpt: Option[WithdrawalEpochCertificate]): Try[SidechainState] = Try {
    val version = new ByteArrayWrapper(versionToBytes(newVersion))
    applicationState.onApplyChanges(this,
      version.data,
      changes.toAppend.map(_.box).asJava,
      changes.toRemove.map(_.boxId.array).asJava) match {
      case Success(appState) =>
        val boxesToUpdate = changes.toAppend.map(_.box).filter(box => !box.isInstanceOf[WithdrawalRequestBox]).toSet
        val boxIdsToRemoveSet = changes.toRemove.map(r => new ByteArrayWrapper(r.boxId)).toSet
        val withdrawalRequestsToAppend = changes.toAppend.map(_.box).filter(box => box.isInstanceOf[WithdrawalRequestBox])
          .map(_.asInstanceOf[WithdrawalRequestBox])
        val forgingStakesToAppend = changes.toAppend.map(_.box).filter(box => box.isInstanceOf[ForgerBox])
          .map(box => ForgingStakeInfo(box.id(), box.value()))

        new SidechainState(
          stateStorage.update(version, withdrawalEpochInfo, boxesToUpdate, boxIdsToRemoveSet,
            withdrawalRequestsToAppend, forgingStakesToAppend, consensusEpoch, withdrawalEpochCertificateOpt).get,
          params,
          newVersion,
          appState
        )
      case Failure(exception) => throw exception
    }
  }.recoverWith{
    case exception =>
      log.error("Exception was thrown during applyChanges.", exception)
      Failure(exception)
  }

  override def maxRollbackDepth: Int = {
    stateStorage.rollbackVersions.size
  }

  override def rollbackTo(to: VersionTag): Try[SidechainState] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    val version = BytesUtils.fromHexString(to)
    applicationState.onRollback(version) match {
      case Success(appState) => new SidechainState(stateStorage.rollback(new ByteArrayWrapper(version)).get, params, to, appState)
      case Failure(exception) => throw exception
    }
  }.recoverWith{case exception =>
    log.error("Exception was thrown during rollback.", exception)
    Failure(exception)
  }

  def isSwitchingConsensusEpoch(mod: SidechainBlock): Boolean = {
    val blockConsensusEpoch: ConsensusEpochNumber = timeStampToEpochNumber(mod.timestamp)
    val currentConsensusEpoch: ConsensusEpochNumber = stateStorage.getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0))

    blockConsensusEpoch != currentConsensusEpoch
  }


  // Returns lastBlockInEpoch and ConsensusEpochInfo for that epoch
  def getCurrentConsensusEpochInfo: (ModifierId, ConsensusEpochInfo) = {
    // TO DO: should be changed, when we will change the structure of SidechainCreation output in MC Tx
    // TO DO: missed forging stake should cause IllegalStateException
    val forgingStakes: Seq[ForgingStakeInfo] = stateStorage.getForgingStakesInfo match {
      case seq if seq.isDefined && seq.get.nonEmpty =>
        seq.get
      case _ => // just a mock for now
        // Note: at least one ForgingStakeInfo must be present. Now NOT, because CreationOutput doesn't return forging box.
        Seq(ForgingStakeInfo(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"), 0L))
    }

    (stateStorage.getConsensusEpochNumber, stateStorage.getForgingStakesAmount) match {
      case (Some(consensusEpochNumber), Some(forgingStakesAmount)) =>
        val lastBlockInEpoch = bytesToId(stateStorage.lastVersionId.get.data) // we use block id as version
        val consensusEpochInfo = ConsensusEpochInfo(
          consensusEpochNumber,
          MerkleTree.createMerkleTree(forgingStakes.map(info => info.boxId).asJava),
          forgingStakesAmount
        )

        (lastBlockInEpoch, consensusEpochInfo)
      case _ =>
        throw new IllegalStateException("Can't retrieve Consensus Epoch related info form StateStorage.")
    }
  }
}

object SidechainState
{
  def changes(mod: SidechainBlock) : Try[BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB]] = Try {
    val initial = (Seq(): Seq[Array[Byte]], Seq(): Seq[SidechainTypes#SCB], 0L)

    val (toRemove: Seq[Array[Byte]], toAdd: Seq[SidechainTypes#SCB], reward) =
      mod.transactions.foldLeft(initial){ case ((sr, sa, f), tx) =>
        (sr ++ tx.unlockers().asScala.map(_.closedBoxId()), sa ++ tx.newBoxes().asScala, f + tx.fee())
      }

    // calculate list of ID of unlokers' boxes -> toRemove
    // calculate list of new boxes -> toAppend
    // calculate the rewards for Miner/Forger -> create another regular tx OR Forger need to add his Reward during block creation
    @SuppressWarnings(Array("org.wartremover.warts.Product","org.wartremover.warts.Serializable"))
    val ops: Seq[BoxStateChangeOperation[SidechainTypes#SCP, SidechainTypes#SCB]] =
      toRemove.map(id => Removal[SidechainTypes#SCP, SidechainTypes#SCB](scorex.crypto.authds.ADKey(id))) ++
      toAdd.map(b => Insertion[SidechainTypes#SCP, SidechainTypes#SCB](b))

    BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB](ops)

    // Q: Do we need to call some static method of ApplicationState?
    // A: Probably yes. To remove some out of date boxes, like VoretBallotRight box for previous voting epoch.
    // Note: we need to implement a lot of limitation for changes from ApplicationState (only deletion, only non coin realted boxes, etc.)
  }

  private[horizen] def restoreState(stateStorage: SidechainStateStorage, params: NetworkParams, applicationState: ApplicationState) : Option[SidechainState] = {

    if (!stateStorage.isEmpty)
      Some(new SidechainState(stateStorage, params, bytesToVersion(stateStorage.lastVersionId.get.data), applicationState))
    else
      None
  }

  private[horizen] def createGenesisState(stateStorage: SidechainStateStorage, params: NetworkParams,
                                          applicationState: ApplicationState,
                                          genesisBlock: SidechainBlock) : Try[SidechainState] = Try {

    if (stateStorage.isEmpty)
      new SidechainState(stateStorage, params, idToVersion(genesisBlock.parentId), applicationState)
        .applyModifier(genesisBlock).get
    else
      throw new RuntimeException("State storage is not empty!")
  }
}

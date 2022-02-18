package com.horizen


import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import com.horizen.consensus._
import com.horizen.node.SidechainNodeView
import com.horizen.params.NetworkParams
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.utils.BytesUtils
import com.horizen.validation._
import com.horizen.wallet.ApplicationWallet
import scorex.core.NodeViewHolder.DownloadRequest
import scorex.core.consensus.History.ProgressInfo
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages._
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.core.{idToVersion, versionToId}
import scorex.util.{ModifierId, ScorexLogging, bytesToId, idToBytes}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class SidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                              historyStorage: SidechainHistoryStorage,
                              consensusDataStorage: ConsensusDataStorage,
                              stateStorage: SidechainStateStorage,
                              forgerBoxStorage: SidechainStateForgerBoxStorage,
                              utxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage,
                              walletBoxStorage: SidechainWalletBoxStorage,
                              secretStorage: SidechainSecretStorage,
                              walletTransactionStorage: SidechainWalletTransactionStorage,
                              forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
                              cswDataStorage: SidechainWalletCswDataStorage,
                              params: NetworkParams,
                              timeProvider: NetworkTimeProvider,
                              applicationWallet: ApplicationWallet,
                              applicationState: ApplicationState,
                              genesisBlock: SidechainBlock)
  extends scorex.core.NodeViewHolder[SidechainTypes#SCBT, SidechainBlock]
  with ScorexLogging
  with SidechainTypes
{
  override type SI = SidechainSyncInfo
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  case class SidechainNodeUpdateInformation(history: HIS,
                                            state: MS,
                                            wallet: VL,
                                            failedMod: Option[SidechainBlock],
                                            alternativeProgressInfo: Option[ProgressInfo[SidechainBlock]],
                                            suffix: IndexedSeq[SidechainBlock])

  override val scorexSettings: ScorexSettings = sidechainSettings.scorexSettings

  private def semanticBlockValidators(params: NetworkParams): Seq[SemanticBlockValidator] = Seq(new SidechainBlockSemanticValidator(params))
  private def historyBlockValidators(params: NetworkParams): Seq[HistoryBlockValidator] = Seq(
    new WithdrawalEpochValidator(params),
    new MainchainPoWValidator(params),
    new MainchainBlockReferenceValidator(params),
    new ConsensusValidator(timeProvider)
  )

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    log.info("Restoring persistent state from storage...")
    val restoredData = for {
      history <- SidechainHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
      state <- SidechainState.restoreState(stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, params, applicationState)
      wallet <- SidechainWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, applicationWallet)
      pool <- Some(SidechainMemoryPool.emptyPool)
    } yield (history, state, wallet, pool)

    if (restoredData.isEmpty) {
      // this is the case for the genesis state
      log.info("Nothing to restore, exiting")
      restoredData
    } else {

      dumpStorages

      log.info("Checking state consistency...")

      val restoredHistory = restoredData.get._1
      val restoredState   = restoredData.get._2
      val restoredWallet  = restoredData.get._3
      val restoredMempool = restoredData.get._4

      val historyVersion = restoredHistory.bestBlockId
      // TODO check that validity of best block info is consistent, that is updated in storage as a last step

      // get common version of the state storages, if necessary some rollback is applied internally
      // according to the update procedure sequence
      val checkedStateData = restoredState.ensureStorageConsistencyAfterRestore
      if (checkedStateData.isFailure) {
        log.error("state storages are not consistent")
        return None
      }

      val checkedState = checkedStateData.get._1
      val stateVersion = checkedStateData.get._2
      log.info(s"history bestBlockId = ${historyVersion}, stateVersion = ${stateVersion}")

      val height_h = restoredHistory.blockInfoById(historyVersion).height
      val height_s = restoredHistory.blockInfoById(bytesToId(stateVersion.data)).height
      log.info(s"history  height = ${height_h}, state height = ${height_s}")

      if (historyVersion == bytesToId(stateVersion.data)) {
        log.info("state and history storages are consistent")

        // get common version of the wallet storages, if necessary some rollback is applied internally
        // according to the update procedure sequence
        val checkedWalletData = restoredWallet.ensureStorageConsistencyAfterRestore
        if (checkedWalletData.isFailure) {
          log.error("wallet storages are not consistent")
          return None
        }

        val checkedWallet = checkedWalletData.get._1
        val walletVersion = checkedWalletData.get._2

        log.info(s"walletVersion = ${walletVersion}")
        if (historyVersion == bytesToId(walletVersion.data)) {
          // This is the successful case
          log.info("state, history and wallet storages are consistent")
          dumpStorages
          Some(restoredHistory, checkedState, checkedWallet, restoredMempool)//restoredData
        }
        else {
          log.error("state and wallet storages are not consistent")
          // wallet and state are not consistent, while state and history are, this should never happen
          // state --> wallet --> history
          None
        }
      } else {
        log.warn("Inconsistent state and history storages, trying to recover...")

        // this is the sequence of blocks starting from active chain up to input block, unless a None is returned in case of errors
        val nonChainSuffix = restoredHistory.chainBack(bytesToId(stateVersion.data), restoredHistory.storage.isInActiveChain, Int.MaxValue)
        log.info(s"sequence of blocks not in active chain (root included) = ${nonChainSuffix}")

        if (nonChainSuffix.isEmpty) {
          log.error("Could not recover storages inconsistency, could not find a rollback point in history")
          None
        } else {
          val rollback_to = nonChainSuffix.get.head
          val child_block = nonChainSuffix.get.tail.headOption
          if (!child_block.isEmpty) {
            log.info(s"Child ${BytesUtils.toHexString(idToBytes(child_block.get))} is in history")
            log.info(s"Child info ${restoredHistory.blockInfoById(child_block.get)}")
          }

          // since the update order is state --> wallet --> history
          // we can rollback both state and wallet to current best block in history or the ancestor of state block in active chain (which might as well be the same)
          log.warn(s"Inconsistent storage and history, rolling back state and wallets to history best block id = ${rollback_to}")

          val rolledBackState  = restoredState.rollbackTo(idToVersion(rollback_to))
          val rolledBackWallet = restoredWallet.rollback(idToVersion(rollback_to))

          (rolledBackState, rolledBackWallet) match {
            case (Success(s), Success(w)) => {
              log.debug("State and wallet succesfully rolled back")
              dumpStorages
              Some((restoredHistory, s, w, restoredMempool))            }
            case (Failure(e), _) => {
              log.error("State roll back failed")
              context.system.eventStream.publish(RollbackFailed)
              None
            }
            case (_, Failure(e)) => {
              log.error("Wallet roll back failed")
              context.system.eventStream.publish(RollbackFailed)
              None
            }
          }
        }
      }
    }
  }

  def dumpStorages : Unit = {
    if (!historyStorage.lastVersionId.isEmpty) { // also other storages should be checked before 'get' methods
      log.debug(s"HistoryStorage:             ${BytesUtils.toHexString(historyStorage.lastVersionId.get.data())}")
      log.debug(s"ConsensusStorage:           ${BytesUtils.toHexString(consensusDataStorage.lastVersionId.get.data())}")
      log.debug(s"secretStorage:              ${BytesUtils.toHexString(secretStorage.lastVersionId.get.data())}")
      log.debug("--------------------")
      log.debug(s"StateStorage:               ${BytesUtils.toHexString(stateStorage.lastVersionId.get.data())}")
      log.debug(s"StateForgerBoxStorage:      ${BytesUtils.toHexString(forgerBoxStorage.lastVersionId.get.data())}")
      log.debug(s"UtxoMerkleTreeStorage:      ${BytesUtils.toHexString(utxoMerkleTreeStorage.lastVersionId.get.data())}")
      log.debug("--------------------")
      log.debug(s"WalletBoxStorage:           ${BytesUtils.toHexString(walletBoxStorage.lastVersionId.get.data())}")
      log.debug(s"WalletTransactionStorage:   ${BytesUtils.toHexString(walletTransactionStorage.lastVersionId.get.data())}")
      log.debug(s"ForgingBoxesInfoStorage:    ${BytesUtils.toHexString(forgingBoxesInfoStorage.lastVersionId.get.data())}")
      log.debug(s"    ForgingBoxesInfoStorage vers:    ${forgingBoxesInfoStorage.rollbackVersions.slice(0, 10)}")
      log.debug(s"CswDataStorage:             ${BytesUtils.toHexString(cswDataStorage.lastVersionId.get.data())}")
//      val m = getStorageVersions.map{ case(k, v) => {k.toString() + ": " + v.toString()}}
//      log.debug(s"${m}")
    }}

  def getStorageVersions : Map[String, String] = {
    val m = mutable.Map[String, String]()
    // TODO fill it. For instance:
    val v = historyStorage.lastVersionId match {
      case Some(value) => BytesUtils.toHexString(value.data())
      case None => ""
    }

    m += (historyStorage.getClass.toString -> v)
    scala.collection.immutable.Map[String, String](m.toList:_*)
  } // TODO

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- SidechainState.createGenesisState(stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, params, applicationState, genesisBlock)

      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getCurrentConsensusEpochInfo)
      withdrawalEpochNumber: Int <- Success(state.getWithdrawalEpochInfo.epoch)

      history <- SidechainHistory.createGenesisHistory(historyStorage, consensusDataStorage, params, genesisBlock, semanticBlockValidators(params),
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake))

      wallet <- SidechainWallet.createGenesisWallet(sidechainSettings.wallet.seed.getBytes, walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, applicationWallet,
        genesisBlock, withdrawalEpochNumber, consensusEpochInfo)

      pool <- Success(SidechainMemoryPool.emptyPool)
    } yield (history, state, wallet, pool)

    result.get
  }

  protected def getCurrentSidechainNodeViewInfo: Receive = {
    case SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(f) => try {
      sender() ! f(new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), minimalState().applicationState, vault().applicationWallet))
    }
    catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e)
    }
  }

  protected def applyFunctionOnNodeView: Receive = {
    case SidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView(function) => try {
      sender() ! function(new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), minimalState().applicationState, vault().applicationWallet))
    }
    catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e)
    }
  }

  protected def applyBiFunctionOnNodeView[T, A]: Receive = {
    case SidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView(function, functionParameter) => try {
      sender() ! function(new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), minimalState().applicationState, vault().applicationWallet), functionParameter)
    }
    catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e)
    }
  }

  protected def processLocallyGeneratedSecret: Receive = {
    case SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret(secret) =>
      vault().addSecret(secret) match {
        case Success(newVault) =>
          updateNodeView(updatedVault = Some(newVault))
          sender() ! Success(Unit)
        case Failure(ex) =>
          sender() ! Failure(ex)
      }
  }

  override def receive: Receive = {
      applyFunctionOnNodeView orElse
      applyBiFunctionOnNodeView orElse
      getCurrentSidechainNodeViewInfo orElse
      processLocallyGeneratedSecret orElse
      super.receive
  }

  // This method is actually a copy-paste of parent NodeViewHolder.pmodModify method.
  // The difference is that modifiers are applied to the State and Wallet simultaneously.
  override protected def pmodModify(pmod: SidechainBlock): Unit = {
    if (!history().contains(pmod.id)) {
      context.system.eventStream.publish(StartingPersistentModifierApplication(pmod))

      log.info(s"Apply modifier ${pmod.encodedId} of type ${pmod.modifierTypeId} to nodeViewHolder")

      history().append(pmod) match {
        case Success((historyBeforeStUpdate, progressInfo)) =>
          log.debug(s"Going to apply modifications to the state: $progressInfo")
          context.system.eventStream.publish(SyntacticallySuccessfulModifier(pmod))
          context.system.eventStream.publish(NewOpenSurface(historyBeforeStUpdate.openSurfaceIds()))

          if (progressInfo.toApply.nonEmpty) {
            val (newHistory, newStateTry, newWallet, blocksApplied) =
              updateStateAndWallet(historyBeforeStUpdate, minimalState(), vault(), progressInfo, IndexedSeq())

            newStateTry match {
              case Success(newState) =>
                val newMemPool = updateMemPool(progressInfo.toRemove, blocksApplied, memoryPool(), newState)
                // Note: in parent NodeViewHolder.pmodModify wallet was updated here.

                log.info(s"Persistent modifier ${pmod.encodedId} applied successfully, now updating node view")
                updateNodeView(Some(newHistory), Some(newState), Some(newWallet), Some(newMemPool))


              case Failure(e) =>
                log.warn(s"Can`t apply persistent modifier (id: ${pmod.encodedId}, contents: $pmod) to minimal state", e)
                updateNodeView(updatedHistory = Some(newHistory))
                context.system.eventStream.publish(SemanticallyFailedModification(pmod, e))
            }
          } else {
            requestDownloads(progressInfo)
            updateNodeView(updatedHistory = Some(historyBeforeStUpdate))
          }
        case Failure(e) =>
          log.warn(s"Can`t apply persistent modifier (id: ${pmod.encodedId}, contents: $pmod) to history", e)
          context.system.eventStream.publish(SyntacticallyFailedModification(pmod, e))
      }
    } else {
      log.warn(s"Trying to apply modifier ${pmod.encodedId} that's already in history")
    }
    dumpStorages
  }

  // This method is actually a copy-paste of parent NodeViewHolder.requestDownloads method.
  protected def requestDownloads(pi: ProgressInfo[SidechainBlock]): Unit = {
    pi.toDownload.foreach { case (tid, id) =>
      context.system.eventStream.publish(DownloadRequest(tid, id))
    }
  }

  // This method is actually a copy-paste of parent NodeViewHolder.updateState method.
  // The difference is that State is updated together with Wallet.
  @tailrec
  private def updateStateAndWallet(history: HIS,
                          state: MS,
                          wallet: VL,
                          progressInfo: ProgressInfo[SidechainBlock],
                          suffixApplied: IndexedSeq[SidechainBlock]): (HIS, Try[MS], VL, Seq[SidechainBlock]) = {
    requestDownloads(progressInfo)

    // Do rollback if chain switch needed
    val (stateToApplyTry: Try[MS], walletToApplyTry: Try[VL], suffixTrimmed: IndexedSeq[SidechainBlock]) = if (progressInfo.chainSwitchingNeeded) {
      @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
      val branchingPoint = progressInfo.branchPoint.get //todo: .get
      if (state.version != branchingPoint) {
        log.debug(s"chain reorg needed, rolling back state and wallet to branching point: ${branchingPoint}")
        (
          state.rollbackTo(idToVersion(branchingPoint)),
          wallet.rollback(idToVersion(branchingPoint)),
          trimChainSuffix(suffixApplied, branchingPoint)
        )
      } else (Success(state), Success(wallet), IndexedSeq())
    } else (Success(state), Success(wallet), suffixApplied)

    (stateToApplyTry, walletToApplyTry) match {
      case (Success(stateToApply), Success(walletToApply)) => {
        log.debug("calling applyStateAndWallet")
        val nodeUpdateInfo = applyStateAndWallet(history, stateToApply, walletToApply, suffixTrimmed, progressInfo)

        nodeUpdateInfo.failedMod match {
          case Some(_) =>
            @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
            val alternativeProgressInfo = nodeUpdateInfo.alternativeProgressInfo.get
            updateStateAndWallet(nodeUpdateInfo.history, nodeUpdateInfo.state, nodeUpdateInfo.wallet, alternativeProgressInfo, nodeUpdateInfo.suffix)
          case None => (nodeUpdateInfo.history, Success(nodeUpdateInfo.state), nodeUpdateInfo.wallet, nodeUpdateInfo.suffix)
        }
      }
      case (Failure(e), _) =>
        log.error("State rollback failed: ", e)
        context.system.eventStream.publish(RollbackFailed)
        //todo: what to return here? the situation is totally wrong
        ???
      case (_, Failure(e)) =>
        log.error("Wallet rollback failed: ", e)
        context.system.eventStream.publish(RollbackFailed)
        //todo: what to return here? the situation is totally wrong
        ???
    }
  }

  // This method is actually a copy-paste of parent NodeViewHolder.trimChainSuffix method.
  protected def trimChainSuffix(suffix: IndexedSeq[SidechainBlock], rollbackPoint: scorex.util.ModifierId): IndexedSeq[SidechainBlock] = {
    val idx = suffix.indexWhere(_.id == rollbackPoint)
    if (idx == -1) IndexedSeq() else suffix.drop(idx)
  }

  // Apply state and wallet with blocks one by one, if consensus epoch is going to be changed -> notify wallet and history.
  protected def applyStateAndWallet(history: HIS,
                           stateToApply: MS,
                           walletToApply: VL,
                           suffixTrimmed: IndexedSeq[SidechainBlock],
                           progressInfo: ProgressInfo[SidechainBlock]): SidechainNodeUpdateInformation = {
    val updateInfoSample = SidechainNodeUpdateInformation(history, stateToApply, walletToApply, None, None, suffixTrimmed)
    progressInfo.toApply.foldLeft(updateInfoSample) { case (updateInfo, modToApply) =>
      if (updateInfo.failedMod.isEmpty) {
        // Check if the next modifier will change Consensus Epoch, so notify History and Wallet with current info.
        val (newHistory, newWallet) = if(updateInfo.state.isSwitchingConsensusEpoch(modToApply)) {
          log.debug("Changing consensus epoch")
          val (lastBlockInEpoch, consensusEpochInfo) = updateInfo.state.getCurrentConsensusEpochInfo
          val nonceConsensusEpochInfo = updateInfo.history.calculateNonceForEpoch(blockIdToEpochId(lastBlockInEpoch))
          val stakeConsensusEpochInfo = StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake)

          val historyAfterConsensusInfoApply =
            updateInfo.history.applyFullConsensusInfo(lastBlockInEpoch, FullConsensusEpochInfo(stakeConsensusEpochInfo, nonceConsensusEpochInfo))

          val walletAfterStakeConsensusApply = updateInfo.wallet.applyConsensusEpochInfo(consensusEpochInfo)
          (historyAfterConsensusInfoApply, walletAfterStakeConsensusApply)
        } else
          (updateInfo.history, updateInfo.wallet)

        // if a crash happens here the inconsistency between state and history wont appear: we should check the wallet storages and if a inconsistency is seen, rollback it
        // we have:
        //   1. state == history
        //   2. (wallet storages set) != state because of forgerBoxStorage
        //   3. history consensus storage has evolved as well but it has no rollback points

        //   At the restart all the update above would be re-applied, but in the meanwhile (before re-update it) such data might be used
        //   for instance in the forging phase or even in the validation phase.
        //   To rule out this possibility, even in case of future modifications,
        //   we can find a common root between state and ForgerBoxStorage versions and roll back up to that point

        log.debug("applying modifier to state, blockInfo: " + history.blockToBlockInfo(modToApply))
        updateInfo.state.applyModifier(modToApply) match {
          case Success(stateAfterApply) => {
            log.debug("success: modifier applied to state, blockInfo: " + newHistory.blockInfoById(modToApply.id))

            context.system.eventStream.publish(SemanticallySuccessfulModifier(modToApply))

            val stateWithdrawalEpochNumber: Int = stateAfterApply.getWithdrawalEpochInfo.epoch
            val walletAfterApply: SidechainWallet = if(stateAfterApply.isWithdrawalEpochLastIndex) {
              newWallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, stateAfterApply.getFeePayments(stateWithdrawalEpochNumber), Some(stateAfterApply))
            } else {
              newWallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, Seq(), None)
            }

            // as a final step update the history (validity and best block info), in this way we can check
            // at the startup the consistency of state and history storage versions and be sure that also intemediate steps
            // are consistent
            val historyAfterApply = newHistory.reportModifierIsValid(modToApply)
            log.debug("success: modifier applied to history, blockInfo " + historyAfterApply.blockInfoById(modToApply.id))

            SidechainNodeUpdateInformation(historyAfterApply, stateAfterApply, walletAfterApply, None, None, updateInfo.suffix :+ modToApply)
          }
          case Failure(e) => {
            log.error(s"Could not apply modifier ${BytesUtils.toHexString(idToBytes(modToApply.id))}, exception:" + e)
            val (historyAfterApply, newProgressInfo) = newHistory.reportModifierIsInvalid(modToApply, progressInfo)
            context.system.eventStream.publish(SemanticallyFailedModification(modToApply, e))
            SidechainNodeUpdateInformation(historyAfterApply, updateInfo.state, newWallet, Some(modToApply), Some(newProgressInfo), updateInfo.suffix)
          }
        }
      } else updateInfo
    }
  }
}

object SidechainNodeViewHolder /*extends ScorexLogging with ScorexEncoding*/ {
  object ReceivableMessages{
    case class GetDataFromCurrentSidechainNodeView[HIS, MS, VL, MP, A](f: SidechainNodeView => A)
    case class ApplyFunctionOnNodeView[HIS, MS, VL, MP, A](f: java.util.function.Function[SidechainNodeView, A])
    case class ApplyBiFunctionOnNodeView[HIS, MS, VL, MP, T, A](f: java.util.function.BiFunction[SidechainNodeView, T, A], functionParameter: T)
    case class LocallyGeneratedSecret[S <: SidechainTypes#SCS](secret: S)
  }
}

object SidechainNodeViewHolderRef {
  def props(sidechainSettings: SidechainSettings,
            historyStorage: SidechainHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateStorage: SidechainStateStorage,
            forgerBoxStorage: SidechainStateForgerBoxStorage,
            utxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage,
            walletBoxStorage: SidechainWalletBoxStorage,
            secretStorage: SidechainSecretStorage,
            walletTransactionStorage: SidechainWalletTransactionStorage,
            forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
            cswDataStorage: SidechainWalletCswDataStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState,
            genesisBlock: SidechainBlock): Props =
    Props(new SidechainNodeViewHolder(sidechainSettings, historyStorage, consensusDataStorage, stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock))

  def apply(sidechainSettings: SidechainSettings,
            historyStorage: SidechainHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateStorage: SidechainStateStorage,
            forgerBoxStorage: SidechainStateForgerBoxStorage,
            utxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage,
            walletBoxStorage: SidechainWalletBoxStorage,
            secretStorage: SidechainSecretStorage,
            walletTransactionStorage: SidechainWalletTransactionStorage,
            forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
            cswDataStorage: SidechainWalletCswDataStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState,
            genesisBlock: SidechainBlock)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(sidechainSettings, historyStorage, consensusDataStorage, stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock))

  def apply(name: String,
            sidechainSettings: SidechainSettings,
            historyStorage: SidechainHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateStorage: SidechainStateStorage,
            forgerBoxStorage: SidechainStateForgerBoxStorage,
            utxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage,
            walletBoxStorage: SidechainWalletBoxStorage,
            secretStorage: SidechainSecretStorage,
            walletTransactionStorage: SidechainWalletTransactionStorage,
            forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
            cswDataStorage: SidechainWalletCswDataStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState,
            genesisBlock: SidechainBlock)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(sidechainSettings, historyStorage, consensusDataStorage, stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock), name)
}

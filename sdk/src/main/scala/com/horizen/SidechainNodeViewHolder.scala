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
import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.{idToVersion, versionToId}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages._
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.{ModifierId, ScorexLogging, idToBytes}

import scala.annotation.tailrec
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
    log.info("Restoring state")
    val op = for {
      history <- SidechainHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
      state <- SidechainState.restoreState(stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, params, applicationState)
      wallet <- SidechainWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, applicationWallet)
      pool <- Some(SidechainMemoryPool.emptyPool)
    } yield (history, state, wallet, pool)

    if (op.isEmpty) {
      // this is the case for the genesis state
      return op
    }

    dumpStorages

    log.info("############################ Checking state consistency...")

    val h = op.get._1
    val s = op.get._2
    val w = op.get._3
    val m = op.get._4

    val bestBlockId = h.bestBlockId
    val stateVersion = s.version

    log.info(s"################ history bestBlockId = ${bestBlockId}")
    log.info(s"################        stateVersion = ${stateVersion}")
    log.info(s"################    walletBoxVersion = ${BytesUtils.toHexString(walletBoxStorage.lastVersionId.get)}")

    val chainTip = h.blockInfoById(bestBlockId)
    val stateTip = h.blockInfoById(versionToId(stateVersion))
    val height_h = chainTip.height
    val height_s = stateTip.height
    log.info(s"################    history h = ${height_h}")
    log.info(s"################      state h = ${height_s}")

    if (bestBlockId == stateVersion) {
      log.info("Consistent storage and history repositories")
      return op
    }
    log.warn("Inconsistent storage and history repositories, trying to recover that")

    // this is the sequence of blocks starting from active chain up to input block, which is the head
    // of the returned sequence, unless a None is returned in case of errors
    val t = h.chainBack(versionToId(stateVersion), h.storage.isInActiveChain, Int.MaxValue)
    log.info(s"################      t = ${t}")

    if (t.isEmpty)
    {
      log.error("Could not recover")
      return None
    } else {
      val rollback_to = t.get.head

      val child_block = t.get.tail.head
      h.storage.blockById(child_block) match {
        case Some(_) =>
          {
            log.info(s"Child ${BytesUtils.toHexString(idToBytes(child_block))} is in history")
            log.info("Child info " + h.blockInfoById(child_block))
            // Issue: after restoring, this child block is never reprocessed again, even if its state is 'Unknown' in history ledger
            // therefore no further blocks are added to the history or the state in the pmodModify
          }
        case None =>
          {
            log.info(s"Child ${BytesUtils.toHexString(idToBytes(child_block))} is NOT in history")
          }
      }
      log.warn(s"Inconsistent storage and history, rolling back state and wallets to history best block id = ${rollback_to}")
      // we can rollback to current best block in history or the ancestor of state block in active chain (which might as well be the same)

      // state rollback -->
      //      if (applicationState rollback ok) -->
      //      stateStorage.rollback
      //      forgerBoxStorage.rollback
      //      utxoMerkleTreeStorage.rollback
      // wallet rollback -->
      //      walletBoxStorage.rollback
      //      walletTransactionStorage.rollback
      //      forgingBoxesInfoStorage.rollback  <----- which version does it use?
      //      cswDataStorage.rollback(version
      //      ----> applicationWallet callback
      val rolledBackState  = s.rollbackTo(idToVersion(rollback_to))
      val rolledBackWallet = w.rollback(idToVersion(rollback_to))

      dumpStorages
      return Some((h, rolledBackState.get, rolledBackWallet.get, m))
    }
    None
  }

  def dumpStorages : Unit = {
    if (historyStorage.lastVersionId.isPresent) { // also other storages should be checked before 'get' methods
      log.info(s"HistoryStorage:             ${BytesUtils.toHexString(historyStorage.lastVersionId.get)}")
      log.info(s"ConsensusStorage:           ${BytesUtils.toHexString(consensusDataStorage.lastVersionId.get)}")
      log.info(s"secretStorage:              ${BytesUtils.toHexString(secretStorage.lastVersionId.get)}")
      log.info("--------------------")
      log.info(s"StateStorage:               ${BytesUtils.toHexString(stateStorage.lastVersionId.get)}")
      log.info(s"StateForgerBoxStorage:      ${BytesUtils.toHexString(forgerBoxStorage.lastVersionId.get)}")
      log.info(s"UtxoMerkleTreeStorage:      ${BytesUtils.toHexString(utxoMerkleTreeStorage.lastVersionId.get)}")
      log.info("--------------------")
      log.info(s"WalletBoxStorage:           ${BytesUtils.toHexString(walletBoxStorage.lastVersionId.get)}")
      log.info(s"WalletTransactionStorage:   ${BytesUtils.toHexString(walletTransactionStorage.lastVersionId.get)}")
      log.info(s"ForgingBoxesInfoStorage:    ${BytesUtils.toHexString(forgingBoxesInfoStorage.lastVersionId.get)}")
      log.info(s"    ForgingBoxesInfoStorage vers:    ${forgingBoxesInfoStorage.rollbackVersions}")
      log.info(s"CswDataStorage:             ${BytesUtils.toHexString(cswDataStorage.lastVersionId.get)}")
    }}

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

        log.debug("applying modifier to state, history blockInfo " + history.blockToBlockInfo(modToApply))
        updateInfo.state.applyModifier(modToApply) match {
          case Success(stateAfterApply) => {
            log.debug("success: modifier applied to state, history blockInfo " + newHistory.blockInfoById(modToApply.id))
            val historyAfterApply = newHistory.reportModifierIsValid(modToApply)
            log.debug("success: modifier applied to history, blockInfo " + historyAfterApply.blockInfoById(modToApply.id))

            context.system.eventStream.publish(SemanticallySuccessfulModifier(modToApply))

            val stateWithdrawalEpochNumber: Int = stateAfterApply.getWithdrawalEpochInfo.epoch
            val walletAfterApply: SidechainWallet = if(stateAfterApply.isWithdrawalEpochLastIndex) {
              newWallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, stateAfterApply.getFeePayments(stateWithdrawalEpochNumber), Some(stateAfterApply))
            } else {
              newWallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, Seq(), None)
            }

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

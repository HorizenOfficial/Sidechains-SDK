package com.horizen

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import com.horizen.chain.FeePaymentsInfo
import com.horizen.consensus._
import com.horizen.node.SidechainNodeView
import com.horizen.params.NetworkParams
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.utils.{BytesUtils, SDKModifiersCache}
import com.horizen.validation._
import com.horizen.wallet.ApplicationWallet
import sparkz.core.NodeViewHolder.ReceivableMessages.{EliminateTransactions, LocallyGeneratedModifier, LocallyGeneratedTransaction, NewTransactions}
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.network.{ConnectedPeer, ModifiersStatus}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages._
import sparkz.core.settings.SparkzSettings
import sparkz.core.transaction.Transaction
import sparkz.core.transaction.state.TransactionValidation
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifiersCache, idToVersion, versionToId}
import sparkz.core.{idToVersion, versionToId}
import scorex.util.{ModifierId, ScorexLogging}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class SidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                              historyStorage: SidechainHistoryStorage,
                              consensusDataStorage: ConsensusDataStorage,
                              stateStorage: SidechainStateStorage,
                              forgerBoxStorage: SidechainStateForgerBoxStorage,
                              utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
                              walletBoxStorage: SidechainWalletBoxStorage,
                              secretStorage: SidechainSecretStorage,
                              walletTransactionStorage: SidechainWalletTransactionStorage,
                              forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
                              cswDataProvider: SidechainWalletCswDataProvider,
                              backupStorage: BackupStorage,
                              params: NetworkParams,
                              timeProvider: NetworkTimeProvider,
                              applicationWallet: ApplicationWallet,
                              applicationState: ApplicationState,
                              genesisBlock: SidechainBlock)
  extends sparkz.core.NodeViewHolder[SidechainTypes#SCBT, SidechainBlock]
    with ScorexLogging
    with SidechainTypes {
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

  override val sparksSettings: SparkzSettings = sidechainSettings.sparkzSettings
  private var applyingBlock: Boolean = false

  lazy val listOfStorageInfo: Seq[SidechainStorageInfo] = Seq[SidechainStorageInfo](
    historyStorage, consensusDataStorage,
    utxoMerkleTreeProvider, stateStorage, forgerBoxStorage,
    secretStorage, walletBoxStorage, walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider)

  val maxTxFee = sidechainSettings.wallet.maxTxFee

  private def semanticBlockValidators(params: NetworkParams): Seq[SemanticBlockValidator] = Seq(new SidechainBlockSemanticValidator(params))

  private def historyBlockValidators(params: NetworkParams): Seq[HistoryBlockValidator] = Seq(
    new WithdrawalEpochValidator(params),
    new MainchainPoWValidator(params),
    new MainchainBlockReferenceValidator(params),
    new ConsensusValidator(timeProvider)
  )

  /**
   * Cache for modifiers. If modifiers are coming out-of-order, they are to be stored in this cache.
   */
  protected override lazy val modifiersCache: ModifiersCache[SidechainBlock, HIS] =
    new SDKModifiersCache[SidechainBlock, HIS](sparksSettings.network.maxModifiersCacheSize)

  // this method is called at the startup after the load of the storages from the persistent db. It might happen that the node was not
  // stopped gracefully and therefore the consistency among storages might not be ensured. This method tries to recover this situation
  def checkAndRecoverStorages(restoredData: Option[(SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool)]):
  Option[(SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool)] = {

    restoredData.flatMap {
      dataOpt => {
        dumpStorages

        log.info("Checking state consistency...")

        val restoredHistory = dataOpt._1
        val restoredState = dataOpt._2
        val restoredWallet = dataOpt._3
        val restoredMempool = dataOpt._4


        val historyStatus : Option[(String, Boolean, Int)] =  restoredHistory.getReindexStatus match {
          case SidechainHistory.ReindexNotInProgress | 0 => {
            Some(idToVersion(restoredHistory.bestBlockId), false, 0)
          }
          case reindexHeight => {
            // best block id is updated in history storage as very last step
            val blockIdOption = restoredHistory.getBlockIdByHeight(reindexHeight)
            if (blockIdOption.isPresent){
              Some(blockIdOption.get(), true, reindexHeight)
            }else{
              log.debug(s"Incosistent reindex state, unable to restore")
              None
            }
          }
        }
        if (historyStatus.isEmpty){
          None
        }else {
          val versionToAlign = historyStatus.get._1
          val isReindexing = historyStatus.get._2
          val reindexHeight = historyStatus.get._3

          // get common version of the state storages, if necessary some rollback is applied internally
          // according to the update procedure sequence
          restoredState.ensureStorageConsistencyAfterRestore match {
            case Success(checkedState) => {
              val checkedStateVersion = checkedState.version
              val height_s = restoredHistory.blockInfoById(versionToId(checkedStateVersion)).height
              if (!isReindexing) {
                log.debug(s"history bestBlockId = ${versionToAlign}, stateVersion = ${checkedStateVersion}")
                val height_h = restoredHistory.blockInfoById(restoredHistory.bestBlockId).height
                log.debug(s"history height = ${height_h}, state height = ${height_s}")
              } else {
                log.debug(s"reindex was in progress - reindex version:  ${versionToAlign}, stateVersion = ${checkedStateVersion}")
                log.debug(s"reindex history height = ${reindexHeight}, state height = ${height_s}")
              }

              if (isReindexing &&
                restoredWallet.version == checkedStateVersion &&
                versionToAlign != checkedStateVersion && reindexHeight == height_s - 1) {
                //the insconsistency between versionToAlign and checkedStateVersion is caoused only  by a not aligned reindex index
                //(we reindexed succesfully step N+1 but stopped the node before updating the reindex height to that)
                //we just need to fix the reindex index
                log.debug(s"Incosistent reindex index detected - fixing reindex height from  = ${reindexHeight} to ${height_s}")
                Some(restoredHistory.updateReindexStatus(height_s), restoredState, restoredWallet, restoredMempool)
              }else if (versionToAlign == checkedStateVersion) {
                log.info("state and history storages are consistent")
                // get common version of the wallet storages, that at this point must be consistent among them
                // since history and state are (according to the update procedure sequence: state --> wallet --> history)
                // if necessary a rollback is applied internally to the forging box info storage, because
                // it might have been updated upon consensus epoch switch even before the state
                restoredWallet.ensureStorageConsistencyAfterRestore match {
                  case Success(checkedWallet) => {
                    val checkedWalletVersion = checkedWallet.version
                    log.info(s"walletVersion = ${checkedWalletVersion}")
                    if (versionToAlign == checkedWalletVersion) {
                      // This is the successful case
                      log.info("state, history and wallet storages are consistent")
                      dumpStorages
                      if (isReindexing && reindexHeight == 0){
                        //reindex was just started but nothing happened before the stop -> go back to not in progress
                        Some(restoredHistory.updateReindexStatus(SidechainHistory.ReindexNotInProgress), checkedState, checkedWallet, restoredMempool)
                      }else{
                        Some(restoredHistory, checkedState, checkedWallet, restoredMempool)
                      }

                    }
                    else {
                      log.error("state and wallet storages are not consistent and could not be recovered")
                      // wallet and state are not consistent, while state and history are, this should never happen
                      // state --> wallet --> history
                      None
                    }
                  }
                  case Failure(e) => {
                    log.error("wallet storages are not consistent", e)
                    None
                  }
                }
              } else {
                log.warn("Inconsistent state and history storages, trying to recover...")
                // this is the sequence of blocks starting from active chain up to input block, unless a None is returned in case of errors
                restoredHistory.chainBack(versionToId(checkedStateVersion), restoredHistory.storage.isInActiveChain, Int.MaxValue) match {
                  case Some(nonChainSuffix) => {
                    log.info(s"sequence of blocks not in active chain (root included) = ${nonChainSuffix}")
                    val rollbackTo = nonChainSuffix.head
                    nonChainSuffix.tail.headOption.foreach(childBlock => {
                      log.debug(s"Child ${childBlock} is in history")
                      log.debug(s"Child info ${restoredHistory.blockInfoById(childBlock)}")
                    })

                    // since the update order is state --> wallet --> history
                    // we can rollback both state and wallet to current best block in history or the ancestor of state block in active chain (which might as well be the same)
                    log.warn(s"Inconsistent storage and history, rolling back state and wallets to history best block id = ${rollbackTo}")

                    val rolledBackWallet = restoredWallet.rollback(idToVersion(rollbackTo))
                    val rolledBackState = restoredState.rollbackTo(idToVersion(rollbackTo))

                    (rolledBackState, rolledBackWallet) match {
                      case (Success(s), Success(w)) =>
                        log.debug("State and wallet succesfully rolled back")
                        dumpStorages
                        Some((restoredHistory, s, w, restoredMempool))
                      case (Failure(e), _) =>
                        log.error("State roll back failed: ", e)
                        context.system.eventStream.publish(RollbackFailed)
                        None
                      case (_, Failure(e)) =>
                        log.error("Wallet roll back failed: ", e)
                        context.system.eventStream.publish(RollbackFailed)
                        None
                    }
                  }
                  case None => {
                    log.error("Could not recover storages inconsistency, could not find a rollback point in history")
                    None
                  }
                }
              }
              }
              case Failure(ex) => {
                log.error("state storages are not consistent and could not be recovered", ex)
                None
              }
            }
        }
      }
    }
  }

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    log.info("Restoring persistent state from storage...")
    val restoredData = for {
      history <- SidechainHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
      state <- SidechainState.restoreState(stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, params, applicationState)
      wallet <- SidechainWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, params, applicationWallet)
      pool <- Some(SidechainMemoryPool.createEmptyMempool(sidechainSettings.mempool))
    } yield (history, state, wallet, pool)

    val result = checkAndRecoverStorages(restoredData)
    if (result.exists(curr => (curr._1.isReindexing()))) {
      val oldHeight = result.get._1.getReindexStatus
      log.info("Resuming reindex from height "+oldHeight)
      self ! SidechainNodeViewHolder.InternalReceivableMessages.ReindexStep(true)
    }
    result
  }


  def dumpStorages: Unit =
    try {
      val m = getStorageVersions.map { case (k, v) => {
        "%-36s".format(k) + ": " + v
      }
      }
      m.foreach(x => log.debug(s"${x}"))
      log.trace(s"    ForgingBoxesInfoStorage vers:    ${forgingBoxesInfoStorage.rollbackVersions.slice(0, 3)}")
    } catch {
      case e: Exception =>
        // can happen during unit test with mocked objects
        log.warn("Could not print debug info about storages: " + e.getMessage)
    }

  def getStorageVersions: Map[String, String] =
    listOfStorageInfo.map(x => {
      x.getStorageName -> x.lastVersionId.map(value => BytesUtils.toHexString(value.data())).getOrElse("")
    }).toMap


  override def postStop(): Unit = {
    log.info("SidechainNodeViewHolder actor is stopping...")
    super.postStop()
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- SidechainState.createGenesisState(stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, backupStorage, params, applicationState, genesisBlock)

      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getCurrentConsensusEpochInfo)
      withdrawalEpochNumber: Int <- Success(state.getWithdrawalEpochInfo.epoch)

      wallet <- SidechainWallet.createGenesisWallet(sidechainSettings.wallet.seed.getBytes, walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, applicationWallet,
        genesisBlock, withdrawalEpochNumber, consensusEpochInfo)

      history <- SidechainHistory.createGenesisHistory(historyStorage, consensusDataStorage, params, genesisBlock, semanticBlockValidators(params),
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake), false)

      pool <- Success(SidechainMemoryPool.createEmptyMempool(sidechainSettings.mempool))
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


  protected def processGetStorageVersions: Receive = {
    case SidechainNodeViewHolder.ReceivableMessages.GetStorageVersions =>
      sender() ! getStorageVersions
  }

  protected def processLocallyGeneratedTransaction: Receive = {
    case newTxs: LocallyGeneratedTransaction[SidechainTypes#SCBT] =>
      newTxs.txs.foreach(tx => {
        if (isReindexing()) {
          context.system.eventStream.publish(FailedTransaction(ModifierId @@ tx.id, new IllegalArgumentException(s"Reindex in progress, unable to process Transaction ${tx.id()}"),
            immediateFailure = true))
        } else if(tx.fee() > maxTxFee) {
          context.system.eventStream.publish(FailedTransaction(ModifierId @@ tx.id, new IllegalArgumentException(s"Transaction ${tx.id()} with fee of ${tx.fee()} exceed the predefined MaxFee of ${maxTxFee}"),
            immediateFailure = true))
        } else {
          txModify(tx)
        }
      })
  }


  /**
   * Process reindex messages
   */
  protected def processReindex: Receive = {
    case SidechainNodeViewHolder.InternalReceivableMessages.ReindexStep(proceedIfNeeded: Boolean) => {

      reindexStep(proceedIfNeeded) match {
        case Success(proceed) => {
          if (proceed) {
            self ! SidechainNodeViewHolder.InternalReceivableMessages.ReindexStep(proceedIfNeeded)
          }
        }
        case Failure(exception) => {
          log.error(s"Reindex step failed with error", exception)
        }
      }
    }
  }

  protected def reindexStep(proceedIfNeeded: Boolean): Try[Boolean]  = Try {
    val currentReindexStatus = history().reindexStatus
    currentReindexStatus match {
      case SidechainHistory.ReindexNotInProgress => {
        //do nothing on this step, is just used to switch to "reindex" mode. Always proceed
        updateNodeView(
          updatedHistory =  Some(history().updateReindexStatus(SidechainHistory.ReindexJustStarted))
        )
        true
      }
      case SidechainHistory.ReindexJustStarted => {
        logger.debug("Reindex initialize and block 1 (genesis)")
        minimalState().startReindex(backupStorage, genesisBlock) match {
          case Success(stateCleaned) => {
            val cinfo: (ModifierId, ConsensusEpochInfo) = stateCleaned.getCurrentConsensusEpochInfo
            val withdrawalEpochNumber: Int = stateCleaned.getWithdrawalEpochInfo.epoch
            vault().startReindex(backupStorage, genesisBlock, withdrawalEpochNumber, cinfo._2) match {
              case Success(walletCleaned) => {
                history().startReindex(params, genesisBlock, semanticBlockValidators(params),
                  historyBlockValidators(params), StakeConsensusEpochInfo(cinfo._2.forgingStakeInfoTree.rootHash(), cinfo._2.forgersStake)) match {
                  case Success(historyCleaned) => {
                    updateNodeView(
                      updatedHistory = Some(historyCleaned),
                      updatedState = Some(stateCleaned),
                      updatedVault = Some(walletCleaned)
                    )
                    proceedIfNeeded
                  }
                  case Failure(e) => {
                    log.error(s"Error during reindex: unable to initialize history", e)
                    throw e //propagate the exception to avoid the execution of the following steps
                  }
                }
              }
              case Failure(e) => {
                log.error(s"Error during reindex: unable to initialize wallet", e)
                throw e //propagate the exception to avoid the execution of the following steps
              }
            }
          }
          case Failure(e) => {
            log.error(s"Error during reindex: unable to initialize state", e)
            throw e //propagate the exception to avoid the execution of the following steps
          }
        }
      }
      case reindexedHeight => {
        val newHeight = reindexedHeight + 1
        logger.debug("Reindexing block " + newHeight)
        val blokIdOptional = history().getBlockIdByHeight(newHeight)
        if (blokIdOptional.isPresent) {
          val blockOptional = history.getBlockById(blokIdOptional.get)
          if (blockOptional.isPresent) {
            val progressInfo = ProgressInfo(None, Seq(), Seq(blockOptional.get()))
            val (newHistory, newStateTry, newWallet, blocksApplied) =
              updateStateAndWallet(history(), minimalState(), vault(), progressInfo, IndexedSeq())
            newStateTry match {
              case Success(newState) => {
                val reindexCompleted = (newHeight == history.height)
                val nextReindexStatus = if (reindexCompleted) SidechainHistory.ReindexNotInProgress else newHeight
                updateNodeView(
                  updatedHistory = Some(newHistory.updateReindexStatus(nextReindexStatus)),
                  updatedState = Some(newState),
                  updatedVault = Some(newWallet)
                )
                if (reindexCompleted) {
                  log.debug("Reindex completed!")
                  false
                } else {
                  proceedIfNeeded
                }
              }
              case Failure(e) => {
                log.error(s"Error during reindex of block at height ${newHeight}", e)
                throw e //propagate the exception to avoid the execution of the following steps
              }
            }
          } else {
            throw new RuntimeException(s"Error during reindex- Unable to find block at height ${newHeight}")
          }
        } else {
          throw new RuntimeException(s"Error during reindex- Unable to find block at height ${newHeight}")
        }
      }
    }
  }

  override def receive: Receive = {
      applyFunctionOnNodeView orElse
      applyBiFunctionOnNodeView orElse
      getCurrentSidechainNodeViewInfo orElse
      processLocallyGeneratedSecret orElse
      processRemoteModifiers orElse
      applyModifier orElse
      processGetStorageVersions orElse
      processLocallyGeneratedTransaction orElse
      transactionsProcessing orElse
      processReindex orElse
      super.receive
  }


  // This method is actually a copy-paste of parent NodeViewHolder method.
  // The difference is that isReindexing() check has been added
  override protected def transactionsProcessing: Receive = {
    case newTxs: NewTransactions[SidechainTypes#SCBT] =>
      if (isReindexing()){
        log.debug("Received NewTransactions while reindex in progress - will be ignored")
      }else{
        newTxs.txs.foreach(txModify)
      }
    case EliminateTransactions(ids) =>
      if (isReindexing()){
        log.debug("Received EliminateTransactions while reindex in progress - will be ignored")
      }else {
        val updatedPool = memoryPool().filter(tx => !ids.contains(tx.id))
        updateNodeView(updatedMempool = Some(updatedPool))
        ids.foreach { id =>
          val e = new Exception("Became invalid")
          context.system.eventStream.publish(FailedTransaction(id, e, immediateFailure = false))
        }
      }
  }

  /**
   * Process new modifiers from remote.
   * Put all candidates to modifiersCache and then try to apply as much modifiers from cache as possible.
   * Clear cache if it's size exceeds size limit.
   * Publish `ModifiersProcessingResult` message with all just applied and removed from cache modifiers.
   */
  override protected def processRemoteModifiers: Receive = {
    case sparkz.core.NodeViewHolder.ReceivableMessages.ModifiersFromRemote(mods: Seq[SidechainBlock]) =>
      if (isReindexing()){
        log.debug("Received remote block while reindex in progress - will be ignored")
      }else{
        mods.foreach(m => modifiersCache.put(m.id, m))

        log.debug(s"Cache size before: ${modifiersCache.size}")

        if (!applyingBlock) {
          applyingBlock = true
          self ! SidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier(Seq())
        }
      }
  }

  def applyModifier: Receive = {
    case SidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier(applied: Seq[SidechainBlock]) => {
      modifiersCache.popCandidate(history()) match {
        case Some(mod) =>
          pmodModify(mod)
          self ! SidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier(mod +: applied)
        case None => {
          val cleared = modifiersCache.cleanOverfull()
          context.system.eventStream.publish(ModifiersProcessingResult(applied, cleared))
          applyingBlock = false
          log.debug(s"Cache size after: ${modifiersCache.size}")
        }
      }
    }
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

                log.debug(s"Current mempool size: ${newMemPool.getSize} transactions - ${newMemPool.usedSizeKBytes}kb (${newMemPool.usedPercentage}%)")
              case Failure(e) =>
                log.warn(s"Can`t apply persistent modifier (id: ${pmod.encodedId}, contents: $pmod) to minimal state", e)
                updateNodeView(updatedHistory = Some(newHistory))
                context.system.eventStream.publish(SemanticallyFailedModification(pmod, e))
            }
          } else {
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

  protected def isReindexing(): Boolean = {
    history().isReindexing()
  }


  // This method is actually a copy-paste of parent NodeViewHolder.updateState method.
  // The difference is that State is updated together with Wallet.
  @tailrec
  private def updateStateAndWallet(history: HIS,
                                   state: MS,
                                   wallet: VL,
                                   progressInfo: ProgressInfo[SidechainBlock],
                                   suffixApplied: IndexedSeq[SidechainBlock]): (HIS, Try[MS], VL, Seq[SidechainBlock]) = {

    // Do rollback if chain switch needed
    val (walletToApplyTry: Try[VL], stateToApplyTry: Try[MS], suffixTrimmed: IndexedSeq[SidechainBlock]) = if (progressInfo.chainSwitchingNeeded) {
      @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
      val branchingPoint = progressInfo.branchPoint.get //todo: .get
      if (state.version != branchingPoint) {
        log.debug(s"chain reorg needed, rolling back state and wallet to branching point: ${branchingPoint}")
        (
          wallet.rollback(idToVersion(branchingPoint)),
          state.rollbackTo(idToVersion(branchingPoint)),
          trimChainSuffix(suffixApplied, branchingPoint)
        )
      } else (Success(wallet), Success(state), IndexedSeq())
    } else (Success(wallet), Success(state), suffixApplied)

    (stateToApplyTry, walletToApplyTry) match {
      case (Success(stateToApply), Success(walletToApply)) =>
        log.debug("calling applyStateAndWallet")
        applyStateAndWallet(history, stateToApply, walletToApply, suffixTrimmed, progressInfo) match {
          case Success(nodeUpdateInfo) =>
            nodeUpdateInfo.failedMod match {
              case Some(_) =>
                @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
                val alternativeProgressInfo = nodeUpdateInfo.alternativeProgressInfo.get
                updateStateAndWallet(nodeUpdateInfo.history, nodeUpdateInfo.state, nodeUpdateInfo.wallet, alternativeProgressInfo, nodeUpdateInfo.suffix)
              case None => (nodeUpdateInfo.history, Success(nodeUpdateInfo.state), nodeUpdateInfo.wallet, nodeUpdateInfo.suffix)
            }
          case Failure(ex) =>
            (history, Failure(ex), wallet, suffixTrimmed)
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

  // This method is actually a copy-paste of parent NodeViewHolder.processLocallyGeneratedModifiers method,
  // with reindex handling added
  protected override def processLocallyGeneratedModifiers: Receive = {
    case lm: LocallyGeneratedModifier[SidechainBlock] =>
      log.info(s"Got locally generated modifier ${lm.pmod.encodedId} of type ${lm.pmod.modifierTypeId}")
      if (!isReindexing()){
        pmodModify(lm.pmod)
      }else{
        log.info("Node is reindexing, modifier will be ignored")
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
                                    progressInfo: ProgressInfo[SidechainBlock]): Try[SidechainNodeUpdateInformation] = {
    val updateInfoSample = SidechainNodeUpdateInformation(history, stateToApply, walletToApply, None, None, suffixTrimmed)
    progressInfo.toApply.foldLeft[Try[SidechainNodeUpdateInformation]](Success(updateInfoSample)) {
      case (f@Failure(ex), _) =>
        log.error("Reporting modifier failed", ex)
        f
      case (success@Success(updateInfo), modToApply) =>
        if (updateInfo.failedMod.isEmpty) {
          // Check if the next modifier will change Consensus Epoch, so notify History and Wallet with current info.
          val (newHistory, newWallet) = if (updateInfo.state.isSwitchingConsensusEpoch(modToApply)) {
            log.debug("Switching consensus epoch")
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

          updateInfo.state.applyModifier(modToApply) match {
            case Success(stateAfterApply) => {
              log.debug("success: modifier applied to state, blockInfo: " + newHistory.blockInfoById(modToApply.id))

              if (!isReindexing()) {
                context.system.eventStream.publish(SemanticallySuccessfulModifier(modToApply))
              }

              val stateWithdrawalEpochNumber: Int = stateAfterApply.getWithdrawalEpochInfo.epoch
              val (historyResult, walletResult) = if (stateAfterApply.isWithdrawalEpochLastIndex) {
                val feePayments = stateAfterApply.getFeePayments(stateWithdrawalEpochNumber)
                var historyAfterUpdateFee = newHistory
                if (feePayments.nonEmpty) {
                  historyAfterUpdateFee = newHistory.updateFeePaymentsInfo(modToApply.id, FeePaymentsInfo(feePayments))
                }

                val walletAfterApply: SidechainWallet = newWallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, feePayments, Some(stateAfterApply))
                (historyAfterUpdateFee, walletAfterApply)
              } else {
                val walletAfterApply: SidechainWallet = newWallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, Seq(), None)
                (newHistory, walletAfterApply)
              }

              // as a final step update the history (validity and best block info), in this way we can check
              // at the startup the consistency of state and history storage versions and be sure that also intermediate steps
              // are consistent
              if (isReindexing()) {
                Try(SidechainNodeUpdateInformation(historyResult, stateAfterApply, walletResult, None, None, updateInfo.suffix :+ modToApply))
              } else {
                historyResult.reportModifierIsValid(modToApply).map(finalHistory =>
                  SidechainNodeUpdateInformation(finalHistory, stateAfterApply, walletResult, None, None, updateInfo.suffix :+ modToApply)
                )
              }
            }

            case Failure(e) => {
              log.error(s"Could not apply modifier ${modToApply.id}, exception:" + e)
              newHistory.reportModifierIsInvalid(modToApply, progressInfo).map {
                case (historyAfterApply, newProgressInfo) =>
                  context.system.eventStream.publish(SemanticallyFailedModification(modToApply, e))
                  SidechainNodeUpdateInformation(historyAfterApply, updateInfo.state, newWallet, Some(modToApply), Some(newProgressInfo), updateInfo.suffix)
              }
            }
          }
        } else success
    }
  }
}

object SidechainNodeViewHolder /*extends ScorexLogging with SparkzEncoding*/ {
  object ReceivableMessages {
    case class GetDataFromCurrentSidechainNodeView[HIS, MS, VL, MP, A](f: SidechainNodeView => A)

    case class ApplyFunctionOnNodeView[HIS, MS, VL, MP, A](f: java.util.function.Function[SidechainNodeView, A])

    case class ApplyBiFunctionOnNodeView[HIS, MS, VL, MP, T, A](f: java.util.function.BiFunction[SidechainNodeView, T, A], functionParameter: T)

    case class LocallyGeneratedSecret[S <: SidechainTypes#SCS](secret: S)

    case object GetStorageVersions

  }

  private[horizen] object InternalReceivableMessages {
    case class ReindexStep(proceedIfNeeded: Boolean)

    case class ApplyModifier(applied: Seq[SidechainBlock])

    sealed trait NewLocallyGeneratedTransactions[TX <: Transaction] {
      val txs: Iterable[TX]
    }

    case class LocallyGeneratedTransaction[TX <: Transaction](tx: TX) extends NewLocallyGeneratedTransactions[TX] {
      override val txs: Iterable[TX] = Iterable(tx)
    }

  }
}

object SidechainNodeViewHolderRef {

  def props(sidechainSettings: SidechainSettings,
            historyStorage: SidechainHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateStorage: SidechainStateStorage,
            forgerBoxStorage: SidechainStateForgerBoxStorage,
            utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
            walletBoxStorage: SidechainWalletBoxStorage,
            secretStorage: SidechainSecretStorage,
            walletTransactionStorage: SidechainWalletTransactionStorage,
            forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
            cswDataProvider: SidechainWalletCswDataProvider,
            backupStorage: BackupStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState,
            genesisBlock: SidechainBlock): Props =
    Props(new SidechainNodeViewHolder(sidechainSettings, historyStorage, consensusDataStorage, stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock)).withMailbox("akka.actor.deployment.prio-mailbox")

  def apply(sidechainSettings: SidechainSettings,
            historyStorage: SidechainHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateStorage: SidechainStateStorage,
            forgerBoxStorage: SidechainStateForgerBoxStorage,
            utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
            walletBoxStorage: SidechainWalletBoxStorage,
            secretStorage: SidechainSecretStorage,
            walletTransactionStorage: SidechainWalletTransactionStorage,
            forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
            cswDataProvider: SidechainWalletCswDataProvider,
            backupStorage: BackupStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState,
            genesisBlock: SidechainBlock)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(sidechainSettings, historyStorage, consensusDataStorage, stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock).withMailbox("akka.actor.deployment.prio-mailbox"))

  def apply(name: String,
            sidechainSettings: SidechainSettings,
            historyStorage: SidechainHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateStorage: SidechainStateStorage,
            forgerBoxStorage: SidechainStateForgerBoxStorage,
            utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
            walletBoxStorage: SidechainWalletBoxStorage,
            secretStorage: SidechainSecretStorage,
            walletTransactionStorage: SidechainWalletTransactionStorage,
            forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
            cswDataProvider: SidechainWalletCswDataProvider,
            backupStorage: BackupStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState,
            genesisBlock: SidechainBlock)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(sidechainSettings, historyStorage, consensusDataStorage, stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock).withMailbox("akka.actor.deployment.prio-mailbox"), name)
}

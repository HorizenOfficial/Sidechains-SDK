package com.horizen


import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.FeePaymentsInfo
import com.horizen.consensus._
import com.horizen.node._
import com.horizen.params.NetworkParams
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.wallet.ApplicationWallet
import sparkz.core.utils.NetworkTimeProvider
import scorex.util.ModifierId
import com.horizen.utils.{BytesUtils, SDKModifiersCache}
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages._
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifiersCache, idToVersion, versionToId}

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

  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock](sidechainSettings, timeProvider) {
  override type HSTOR = SidechainHistoryStorage
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  lazy val listOfStorageInfo: Seq[SidechainStorageInfo] = Seq[SidechainStorageInfo](
    historyStorage, consensusDataStorage,
    utxoMerkleTreeProvider, stateStorage, forgerBoxStorage,
    secretStorage, walletBoxStorage, walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider)


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

        // best block id is updated in history storage as very last step
        val historyVersion = idToVersion(restoredHistory.bestBlockId)

        // get common version of the state storages, if necessary some rollback is applied internally
        // according to the update procedure sequence
        restoredState.ensureStorageConsistencyAfterRestore match {
          case Success(checkedState) => {
            val checkedStateVersion = checkedState.version

            log.debug(s"history bestBlockId = ${historyVersion}, stateVersion = ${checkedStateVersion}")

            val height_h = restoredHistory.blockInfoById(restoredHistory.bestBlockId).height
            val height_s = restoredHistory.blockInfoById(versionToId(checkedStateVersion)).height
            log.debug(s"history height = ${height_h}, state height = ${height_s}")

            if (historyVersion == checkedStateVersion) {
              log.info("state and history storages are consistent")

              // get common version of the wallet storages, that at this point must be consistent among them
              // since history and state are (according to the update procedure sequence: state --> wallet --> history)
              // if necessary a rollback is applied internally to the forging box info storage, because
              // it might have been updated upon consensus epoch switch even before the state
              restoredWallet.ensureStorageConsistencyAfterRestore match {
                case Success(checkedWallet) => {
                  val checkedWalletVersion = checkedWallet.version
                  log.info(s"walletVersion = ${checkedWalletVersion}")
                  if (historyVersion == checkedWalletVersion) {
                    // This is the successful case
                    log.info("state, history and wallet storages are consistent")
                    dumpStorages
                    Some(restoredHistory, checkedState, checkedWallet, restoredMempool)
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
    result
  }

  override def dumpStorages: Unit =
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

  override def getStorageVersions: Map[String, String] =
    listOfStorageInfo.map(x => {
      x.getStorageName -> x.lastVersionId.map(value => BytesUtils.toHexString(value.data())).getOrElse("")
    }).toMap

  override def processLocallyGeneratedTransaction: Receive = {
    case newTxs: LocallyGeneratedTransaction[SidechainTypes#SCBT] =>
      newTxs.txs.foreach(tx => {
        if (tx.fee() > maxTxFee)
          context.system.eventStream.publish(FailedTransaction(tx.asInstanceOf[Transaction].id, new IllegalArgumentException(s"Transaction ${tx.id()} with fee of ${tx.fee()} exceed the predefined MaxFee of ${maxTxFee}"),
            immediateFailure = true))
        else
          txModify(tx)

      })
  }

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
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake))

      pool <- Success(SidechainMemoryPool.createEmptyMempool(sidechainSettings.mempool))
    } yield (history, state, wallet, pool)

    result.get
  }

  override protected def getCurrentSidechainNodeViewInfo: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView[
      SidechainTypes#SCBT,
      SidechainBlockHeader,
      SidechainBlock,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      _] @unchecked =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(f) => try {
          val l: SidechainNodeView = new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), applicationState, applicationWallet)
          sender() ! f(l)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }

      }
  }


  override protected def applyFunctionOnNodeView: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[
      SidechainTypes#SCBT,
      SidechainBlockHeader,
      SidechainBlock,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      _]@unchecked =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView(f) => try {
          val l: SidechainNodeView = new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), applicationState, applicationWallet)
          sender() ! f(l)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }
      }
  }

  override protected def applyBiFunctionOnNodeView[T, A]: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView[
      SidechainTypes#SCBT,
      SidechainBlockHeader,
      SidechainBlock,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      T, A]@unchecked =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView(f, functionParams) => try {
          val l: SidechainNodeView = new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), applicationState, applicationWallet)
          sender() ! f(l, functionParams)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }
      }
  }


  // Check if the next modifier will change Consensus Epoch, so notify History and Wallet with current info.
  override protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: SidechainBlock): (HIS, VL) = {
    if (state.isSwitchingConsensusEpoch(modToApply)) {
      val (lastBlockInEpoch: ModifierId, consensusEpochInfo: ConsensusEpochInfo) = state.getCurrentConsensusEpochInfo
      val nonceConsensusEpochInfo = history.calculateNonceForEpoch(blockIdToEpochId(lastBlockInEpoch))
      val stakeConsensusEpochInfo = StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake)

      val historyAfterConsensusInfoApply = history.applyFullConsensusInfo(lastBlockInEpoch,
        FullConsensusEpochInfo(stakeConsensusEpochInfo, nonceConsensusEpochInfo))

      val walletAfterStakeConsensusApply = wallet.applyConsensusEpochInfo(consensusEpochInfo)

      (historyAfterConsensusInfoApply, walletAfterStakeConsensusApply)
    } else
      (history, wallet)
  }

  // Check is the modifier ends the withdrawal epoch, so notify History and Wallet about fees to be payed.
  // Scan modifier by the Wallet considering the forger fee payments.
  override protected def scanBlockWithFeePayments(history: HIS, state: MS, wallet: VL, modToApply: SidechainBlock): (HIS, VL) = {
    val stateWithdrawalEpochNumber: Int = state.getWithdrawalEpochInfo.epoch
    if (state.isWithdrawalEpochLastIndex) {
      val feePayments = state.getFeePayments(stateWithdrawalEpochNumber)
      val historyAfterUpdateFee = history.updateFeePaymentsInfo(modToApply.id, FeePaymentsInfo(feePayments))

      val walletAfterApply: VL = wallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, feePayments, Some(state))

      (historyAfterUpdateFee, walletAfterApply)
    } else {
      val walletAfterApply: VL = wallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, Seq(), None)
      (history, walletAfterApply)
    }
  }

  // Apply state and wallet with blocks one by one, if consensus epoch is going to be changed -> notify wallet and history.
  override protected def applyStateAndWallet(history: HIS,
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

              context.system.eventStream.publish(SemanticallySuccessfulModifier(modToApply))

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
              historyResult.reportModifierIsValid(modToApply).map { newHistory =>
                log.debug("success: modifier applied to history, blockInfo " + newHistory.blockInfoById(modToApply.id))

                SidechainNodeUpdateInformation(newHistory, stateAfterApply, walletResult, None, None, updateInfo.suffix :+ modToApply)
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


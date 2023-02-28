package com.horizen.utxo

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.consensus._
import com.horizen.params.NetworkParams
import com.horizen.storage._
import com.horizen.transaction.Transaction
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.history.SidechainHistory
import com.horizen.utxo.mempool.SidechainMemoryPool
import com.horizen.utxo.node.SidechainNodeView
import com.horizen.utxo.state.{ApplicationState, SidechainState, SidechainStateUtxoMerkleTreeProvider}
import com.horizen.utxo.storage._
import com.horizen.utxo.wallet.{ApplicationWallet, SidechainWallet, SidechainWalletCswDataProvider}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages._
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{idToVersion, versionToId}
import sparkz.util.ModifierId

import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success}

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
  override type FPI = SidechainFeePaymentsInfo
  override type NV = SidechainNodeView


  lazy val listOfStorageInfo: Seq[SidechainStorageInfo] = Seq[SidechainStorageInfo](
    historyStorage, consensusDataStorage,
    utxoMerkleTreeProvider, stateStorage, forgerBoxStorage,
    secretStorage, walletBoxStorage, walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider)

  // this method is called at the startup after the load of the storages from the persistent db. It might happen that the node was not
  // stopped gracefully and therefore the consistency among storages might not be ensured. This method tries to recover this situation
  override def checkAndRecoverStorages(restoredData: Option[(SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool)]):
  Option[(SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool)] = {

    restoredData.flatMap {
      dataOpt => {
        dumpStorages()

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
      wallet <- SidechainWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes(StandardCharsets.UTF_8), walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, params, applicationWallet)
      pool <- Some(SidechainMemoryPool.createEmptyMempool(sidechainSettings.mempool))
    } yield (history, state, wallet, pool)

    val result = checkAndRecoverStorages(restoredData)
    result
  }

  override protected def applyLocallyGeneratedTransactions(newTxs: Iterable[SidechainTypes#SCBT]): Unit = {
    newTxs.foreach(tx => {
      if (tx.fee() > maxTxFee)
        context.system.eventStream.publish(FailedTransaction(tx.asInstanceOf[Transaction].id, new IllegalArgumentException(s"Transaction ${tx.id()} with fee of ${tx.fee()} exceed the predefined MaxFee of ${maxTxFee}"),
          immediateFailure = true))
      else
        txModify(tx)

    })
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- SidechainState.createGenesisState(stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, backupStorage, params, applicationState, genesisBlock)

      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getCurrentConsensusEpochInfo)
      withdrawalEpochNumber: Int <- Success(state.getWithdrawalEpochInfo.epoch)

      wallet <- SidechainWallet.createGenesisWallet(sidechainSettings.wallet.seed.getBytes(StandardCharsets.UTF_8), walletBoxStorage, secretStorage,
        walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, applicationWallet,
        genesisBlock, withdrawalEpochNumber, consensusEpochInfo)

      history <- SidechainHistory.createGenesisHistory(historyStorage, consensusDataStorage, params, genesisBlock, semanticBlockValidators(params),
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake))

      pool <- Success(SidechainMemoryPool.createEmptyMempool(sidechainSettings.mempool))
    } yield (history, state, wallet, pool)

    result.get
  }

  override protected def getNodeView(): SidechainNodeView = new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), applicationState, applicationWallet)

  override def getFeePaymentsInfo(state: MS, epochNumber: Int) : FPI = {
    val feePayments = state.getFeePayments(epochNumber)
    SidechainFeePaymentsInfo(feePayments)
  }

  override def getScanPersistentWallet(modToApply: SidechainBlock, stateOp: Option[MS], epochNumber: Int, wallet: VL) : VL = {
    stateOp match {
      case Some(state) =>
        wallet.scanPersistent(modToApply, epochNumber, state.getFeePayments(epochNumber), stateOp)
      case None =>
        wallet.scanPersistent(modToApply, epochNumber, Seq(), None)
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
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock))

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
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataProvider, backupStorage, params, timeProvider, applicationWallet, applicationState, genesisBlock), name)
}

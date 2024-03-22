package io.horizen.account

import akka.actor.{ActorRef, ActorSystem, Props}
import io.horizen.account.AccountSidechainNodeViewHolder.NewExecTransactionsEvent
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.history.AccountHistory
import io.horizen.account.history.validation.{BaseFeeBlockValidator, ChainIdBlockSemanticValidator}
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.node.AccountNodeView
import io.horizen.account.state._
import io.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.wallet.AccountWallet
import io.horizen.consensus._
import io.horizen.history.validation.{HistoryBlockValidator, SemanticBlockValidator}
import io.horizen.params.NetworkParams
import io.horizen.storage.{SidechainSecretStorage, SidechainStorageInfo}
import io.horizen.{AbstractSidechainNodeViewHolder, NodeViewHolderForSeederNode, SidechainSettings, SidechainTypes}
import io.horizen.evm.Database
import sparkz.util.{ModifierId, bytesToId}
import sparkz.core.idToVersion
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, NodeViewHolderEvent, RollbackFailed}
import sparkz.core.utils.NetworkTimeProvider

import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success}

class AccountSidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                                     params: NetworkParams,
                                     timeProvider: NetworkTimeProvider,
                                     historyStorage: AccountHistoryStorage,
                                     consensusDataStorage: ConsensusDataStorage,
                                     stateMetadataStorage: AccountStateMetadataStorage,
                                     stateDbStorage: Database,
                                     customMessageProcessors: Seq[MessageProcessor],
                                     secretStorage: SidechainSecretStorage,
                                     genesisBlock: AccountBlock)
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock](sidechainSettings, timeProvider, params)
  with AccountEventNotifier {

  override type HSTOR = AccountHistoryStorage
  override type HIS = AccountHistory
  override type MS = AccountState
  override type VL = AccountWallet
  override type MP = AccountMemoryPool
  override type FPI = AccountFeePaymentsInfo
  override type NV = AccountNodeView


  protected def messageProcessors(params: NetworkParams): Seq[MessageProcessor] = {
      MessageProcessorUtil.getMessageProcessorSeq(params, customMessageProcessors)
  }

  override def semanticBlockValidators(params: NetworkParams): Seq[SemanticBlockValidator[AccountBlock]] = {
    super.semanticBlockValidators(params) :+ ChainIdBlockSemanticValidator(params)
  }

  override def historyBlockValidators(params: NetworkParams): Seq[HistoryBlockValidator[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountFeePaymentsInfo, AccountHistoryStorage, AccountHistory]] = {
    super.historyBlockValidators(params) :+ BaseFeeBlockValidator()
  }

  override def checkAndRecoverStorages(restoredData: Option[(AccountHistory, AccountState, AccountWallet, AccountMemoryPool)]): Option[(AccountHistory, AccountState, AccountWallet, AccountMemoryPool)] = {
    restoredData.flatMap {
      dataOpt => {
        dumpStorages()

        log.info("Checking state consistency...")
        val restoredHistory = dataOpt._1
        val restoredState = dataOpt._2
        val restoredWallet = dataOpt._3
        val restoredMempool = dataOpt._4

        // best block id is updated in history storage as very last step
        val historyVersion = restoredHistory.bestBlockId
        val checkedStateVersion = bytesToId(stateMetadataStorage.lastVersionId.get.data())

        log.debug(s"history bestBlockId = $historyVersion, stateVersion = $checkedStateVersion")

        log.whenDebugEnabled {
          val height_h = restoredHistory.blockInfoById(restoredHistory.bestBlockId).height
          val height_s = restoredHistory.blockInfoById(checkedStateVersion).height
          s"history height = $height_h, state height = $height_s"
        }

        if (historyVersion == checkedStateVersion) {
          log.info("state and history storages are consistent")
          Some(restoredHistory, restoredState, restoredWallet, restoredMempool)
        } else {
          log.warn("Inconsistent state and history storages, trying to recover...")

          // this is the sequence of blocks starting from active chain up to input block, unless a None is returned in case of errors
          restoredHistory.chainBack(checkedStateVersion, restoredHistory.storage.isInActiveChain, Int.MaxValue) match {
            case Some(nonChainSuffix) =>
              log.info(s"sequence of blocks not in active chain (root included) = $nonChainSuffix")
              val rollbackTo = nonChainSuffix.head
              nonChainSuffix.tail.headOption.foreach(childBlock => {
                log.debug(s"Child $childBlock is in history")
                log.debug(s"Child info ${restoredHistory.blockInfoById(childBlock)}")
              })

              log.warn(s"Inconsistent storage and history, rolling back state to history best block id = $rollbackTo")

              restoredState.rollbackTo(idToVersion(rollbackTo)) match {
                case Success(s) =>
                  log.debug("State successfully rolled back")
                  dumpStorages()
                  // We are done with the recovery. The evm-state storage does not need to be dealt with. A consistent
                  // view of it will be built using the db-root got from the metadata state db, that is now consistent
                  // with history storage
                  Some((restoredHistory, s, restoredWallet, restoredMempool))
                case Failure(e) =>
                  log.error("State roll back failed: ", e)
                  context.system.eventStream.publish(RollbackFailed)
                  None
              }
            case None =>
              log.error("Could not recover storages inconsistency, could not find a rollback point in history")
              None
          }
        }
      }
    }
  }

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    log.info("Restoring persistent state from storage...")

    val restoredData = for {
      history <- AccountHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
      state <- AccountState.restoreState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, timeProvider, blockHashProvider)
      wallet <- AccountWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes(StandardCharsets.UTF_8), secretStorage)
      pool <- Some(AccountMemoryPool.createEmptyMempool(() => minimalState(),
        () => minimalState(),
        sidechainSettings.accountMempool,
        () => this))
    } yield (history, state, wallet, pool)

    val result = checkAndRecoverStorages(restoredData)
    result
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- AccountState.createGenesisState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, timeProvider, blockHashProvider, genesisBlock)
      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getCurrentConsensusEpochInfo)
      history <- AccountHistory.createGenesisHistory(historyStorage, consensusDataStorage, params, genesisBlock, semanticBlockValidators(params),
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake))
      wallet <- AccountWallet.createGenesisWallet(sidechainSettings.wallet.seed.getBytes(StandardCharsets.UTF_8), secretStorage)
      pool <- Success(AccountMemoryPool.createEmptyMempool(() => minimalState(),
                                                           () => minimalState(),
                                                           sidechainSettings.accountMempool,
                                                           () => this))
    } yield (history, state, wallet, pool)

    result.get
  }

  private def blockHashProvider(height: Int): Option[String] = history.blockIdByHeight(height)

  override def getFeePaymentsInfo(state: MS, withdrawalEpochNumber: Int) : FPI = {
    val consensusEpochNumber: ConsensusEpochNumber = state.getCurrentConsensusEpochInfo._2.epoch
    val (feePayments, _) = state.getFeePaymentsInfo(withdrawalEpochNumber, consensusEpochNumber)
    AccountFeePaymentsInfo(feePayments)
  }

  override def getScanPersistentWallet(modToApply: AccountBlock, stateOp: Option[MS], epochNumber: Int, wallet: VL) : VL = {
    wallet.scanPersistent(modToApply)
  }

  override protected def getNodeView(): AccountNodeView = new AccountNodeView(history(), minimalState(), vault(), memoryPool())

  override lazy val listOfStorageInfo: Seq[SidechainStorageInfo] = Seq[SidechainStorageInfo](
    historyStorage, consensusDataStorage, stateMetadataStorage, secretStorage)

  override protected def applyLocallyGeneratedTransactions(newTxs: Iterable[SidechainTypes#SCAT]): Unit = {
    newTxs.foreach {
      case tx if Some(tx).filter(_.isInstanceOf[EthereumTransaction]).map(_.asInstanceOf[EthereumTransaction])
        .exists(ethTx => !sidechainSettings.accountMempool.allowUnprotectedTxs && ethTx.isLegacy && !ethTx.isEIP155) =>
        context.system.eventStream.publish(
          FailedTransaction(
            tx.id,
            new IllegalArgumentException("Legacy unprotected transactions are not allowed."),
            immediateFailure = true
          )
        )
      case tx =>
        log.info(s"Got locally generated tx ${tx.id} of type ${tx.modifierTypeId}")

        txModify(tx)
    }
  }

  override protected def updateMemPool(removedBlocks: Seq[AccountBlock], appliedBlocks: Seq[AccountBlock], memPool: MP, state: MS): MP = {
    memPool.updateMemPool(removedBlocks, appliedBlocks)
  }

  override def sendNewExecTxsEvent(listOfNewExecTxs: Iterable[SidechainTypes#SCAT]): Unit = {
    context.system.eventStream.publish(NewExecTransactionsEvent(listOfNewExecTxs))
  }
}

/* In a Seeder node transactions handling is disabled, so there is a specific NodeViewHolder */
class AccountSidechainNodeViewHolderForSeederNode(sidechainSettings: SidechainSettings,
                                     params: NetworkParams,
                                     timeProvider: NetworkTimeProvider,
                                     historyStorage: AccountHistoryStorage,
                                     consensusDataStorage: ConsensusDataStorage,
                                     stateMetadataStorage: AccountStateMetadataStorage,
                                     stateDbStorage: Database,
                                     customMessageProcessors: Seq[MessageProcessor],
                                     secretStorage: SidechainSecretStorage,
                                     genesisBlock: AccountBlock)
  extends  AccountSidechainNodeViewHolder(sidechainSettings,
    params,
    timeProvider,
    historyStorage,
    consensusDataStorage,
    stateMetadataStorage,
    stateDbStorage,
    customMessageProcessors,
    secretStorage,
    genesisBlock)
    with NodeViewHolderForSeederNode[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock]


object AccountSidechainNodeViewHolder {

  case class NewExecTransactionsEvent(newExecTxs: Iterable[SidechainTypes#SCAT]) extends NodeViewHolderEvent

}

object AccountNodeViewHolderRef {

  private def createNodeViewHolder(sidechainSettings: SidechainSettings,
                                   historyStorage: AccountHistoryStorage,
                                   consensusDataStorage: ConsensusDataStorage,
                                   stateMetadataStorage: AccountStateMetadataStorage,
                                   stateDbStorage: Database,
                                   customMessageProcessors: Seq[MessageProcessor],
                                   secretStorage: SidechainSecretStorage,
                                   params: NetworkParams,
                                   timeProvider: NetworkTimeProvider,
                                   genesisBlock: AccountBlock): AccountSidechainNodeViewHolder = {
    if (isASeederNode(params))
      new AccountSidechainNodeViewHolderForSeederNode(sidechainSettings, params, timeProvider, historyStorage,
        consensusDataStorage, stateMetadataStorage, stateDbStorage, customMessageProcessors, secretStorage, genesisBlock)
    else
      new AccountSidechainNodeViewHolder(sidechainSettings, params, timeProvider, historyStorage,
        consensusDataStorage, stateMetadataStorage, stateDbStorage, customMessageProcessors, secretStorage, genesisBlock)

  }

  private def isASeederNode(params: NetworkParams): Boolean = !params.isHandlingTransactionsEnabled

  def props(sidechainSettings: SidechainSettings,
            historyStorage: AccountHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateMetadataStorage: AccountStateMetadataStorage,
            stateDbStorage: Database,
            customMessageProcessors: Seq[MessageProcessor],
            secretStorage: SidechainSecretStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            genesisBlock: AccountBlock): Props =
    Props(createNodeViewHolder(sidechainSettings, historyStorage, consensusDataStorage, stateMetadataStorage, stateDbStorage,
      customMessageProcessors, secretStorage, params, timeProvider, genesisBlock)).withMailbox("akka.actor.deployment.prio-mailbox")

  def apply(sidechainSettings: SidechainSettings,
            historyStorage: AccountHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateMetadataStorage: AccountStateMetadataStorage,
            stateDbStorage: Database,
            customMessageProcessors: Seq[MessageProcessor],
            secretStorage: SidechainSecretStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            genesisBlock: AccountBlock)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(sidechainSettings, historyStorage, consensusDataStorage, stateMetadataStorage, stateDbStorage,
      customMessageProcessors, secretStorage, params, timeProvider, genesisBlock))

  def apply(name: String,
            sidechainSettings: SidechainSettings,
            historyStorage: AccountHistoryStorage,
            consensusDataStorage: ConsensusDataStorage,
            stateMetadataStorage: AccountStateMetadataStorage,
            stateDbStorage: Database,
            customMessageProcessors: Seq[MessageProcessor],
            secretStorage: SidechainSecretStorage,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            genesisBlock: AccountBlock)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(sidechainSettings, historyStorage, consensusDataStorage, stateMetadataStorage, stateDbStorage,
      customMessageProcessors, secretStorage, params, timeProvider, genesisBlock), name)

}


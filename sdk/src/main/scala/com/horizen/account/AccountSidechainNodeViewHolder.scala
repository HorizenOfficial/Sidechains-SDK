package com.horizen.account

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state._
import com.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import com.horizen.account.transaction.AccountTransaction
import com.horizen.account.validation.{BaseFeeBlockValidator, ChainIdBlockSemanticValidator}
import com.horizen.account.wallet.AccountWallet
import com.horizen.consensus._
import com.horizen.evm.Database
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.storage.{SidechainSecretStorage, SidechainStorageInfo}
import com.horizen.validation.{HistoryBlockValidator, SemanticBlockValidator}
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainSettings, SidechainTypes}
import scorex.util.{ModifierId, bytesToId}
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.RollbackFailed
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.idToVersion
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
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock](sidechainSettings, timeProvider){

  override type HSTOR = AccountHistoryStorage
  override type HIS = AccountHistory
  override type MS = AccountState
  override type VL = AccountWallet
  override type MP = AccountMemoryPool
  override type FPI = AccountFeePaymentsInfo

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

        val height_h = restoredHistory.blockInfoById(restoredHistory.bestBlockId).height
        val height_s = restoredHistory.blockInfoById(checkedStateVersion).height
        log.debug(s"history height = $height_h, state height = $height_s")

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
                  log.debug("State succesfully rolled back")
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
      state <- AccountState.restoreState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, timeProvider)
      wallet <- AccountWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, secretStorage)
      pool <- Some(AccountMemoryPool.createEmptyMempool(() => minimalState()))
    } yield (history, state, wallet, pool)

    val result = checkAndRecoverStorages(restoredData)
    result
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- AccountState.createGenesisState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, timeProvider, genesisBlock)

      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getCurrentConsensusEpochInfo)

      history <- AccountHistory.createGenesisHistory(historyStorage, consensusDataStorage, params, genesisBlock, semanticBlockValidators(params),
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake))

      wallet <- AccountWallet.createGenesisWallet(sidechainSettings.wallet.seed.getBytes, secretStorage)

      pool <- Success(AccountMemoryPool.createEmptyMempool(() => minimalState()))
    } yield (history, state, wallet, pool)

    result.get
  }

  // Check if the next modifier will change Consensus Epoch, so notify History with current info.
  // Note: there is no need to store any info in the Wallet, since for Account model Forger is able
  // to get all necessary information from the State.
  override protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: AccountBlock): (HIS, VL) = {
     val historyAfterConsensusInfoApply = if (state.isSwitchingConsensusEpoch(modToApply.timestamp)) {
      val (lastBlockInEpoch: ModifierId, consensusEpochInfo: ConsensusEpochInfo) = state.getCurrentConsensusEpochInfo
      val nonceConsensusEpochInfo = history.calculateNonceForEpoch(blockIdToEpochId(lastBlockInEpoch))
      val stakeConsensusEpochInfo = StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake)

      history.applyFullConsensusInfo(lastBlockInEpoch,
        FullConsensusEpochInfo(stakeConsensusEpochInfo, nonceConsensusEpochInfo))
    } else {
       history
     }

    (historyAfterConsensusInfoApply, wallet)
  }

  override def getFeePaymentsInfo(state: MS, epochNumber: Int) : FPI = {
    val feePayments = state.getFeePayments(epochNumber)
    AccountFeePaymentsInfo(feePayments)
  }

  override def getScanPersistentWallet(modToApply: AccountBlock, stateOp: Option[MS], epochNumber: Int, wallet: VL) : VL = {
    wallet.scanPersistent(modToApply)
  }

  override protected def getCurrentSidechainNodeViewInfo: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView[
      SidechainTypes#SCAT,
      AccountBlockHeader,
      AccountBlock,
      AccountFeePaymentsInfo,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView,
      _] @unchecked =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(f) => try {
          val l: AccountNodeView = new AccountNodeView(history(), minimalState(), vault(), memoryPool())
          sender() ! f(l)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }

      }
  }

  override protected def applyFunctionOnNodeView: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[
      SidechainTypes#SCAT,
      AccountBlockHeader,
      AccountBlock,
      AccountFeePaymentsInfo,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView,
      _] @unchecked =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView(f) => try {
          val l: AccountNodeView = new AccountNodeView(history(), minimalState(), vault(), memoryPool())
          sender() ! f(l)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }

      }

  }

  override protected def applyBiFunctionOnNodeView[T, A]: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView[
      SidechainTypes#SCAT,
      AccountBlockHeader,
      AccountBlock,
      AccountFeePaymentsInfo,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView,
      T,A] @unchecked =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView(f,functionParams) => try {
          val l: AccountNodeView = new AccountNodeView(history(), minimalState(), vault(), memoryPool())
          sender() ! f(l,functionParams)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }

      }
  }

  override val listOfStorageInfo: Seq[SidechainStorageInfo] = Seq[SidechainStorageInfo](
    historyStorage, consensusDataStorage, stateMetadataStorage, secretStorage)


  override def processLocallyGeneratedTransaction: Receive = {
    case newTxs: LocallyGeneratedTransaction[SidechainTypes#SCAT] =>
      newTxs.txs.foreach(tx => {
        // TODO FOR MERGE - Any custom implementation?
        /*
        if (tx.fee() > maxTxFee)
          context.system.eventStream.publish(FailedTransaction(tx.asInstanceOf[Transaction].id, new IllegalArgumentException(s"Transaction ${tx.id()} with fee of ${tx.fee()} exceed the predefined MaxFee of ${maxTxFee}"),
            immediateFailure = true))
        else

         */
        log.info(s"Got locally generated tx ${tx.id} of type ${tx.modifierTypeId}")

        txModify(tx)

      })
  }

  override protected def updateMemPool(removedBlocks: Seq[AccountBlock], appliedBlocks: Seq[AccountBlock], memPool: MP, state: MS): MP = {
    memPool.updateMemPool(removedBlocks, appliedBlocks)
  }

}

object AccountNodeViewHolderRef {
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
    Props(new AccountSidechainNodeViewHolder(sidechainSettings, params, timeProvider, historyStorage,
      consensusDataStorage, stateMetadataStorage, stateDbStorage, customMessageProcessors, secretStorage, genesisBlock)).withMailbox("akka.actor.deployment.prio-mailbox")

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
      customMessageProcessors, secretStorage, params, timeProvider, genesisBlock).withMailbox("akka.actor.deployment.prio-mailbox"))

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
      customMessageProcessors, secretStorage, params, timeProvider, genesisBlock).withMailbox("akka.actor.deployment.prio-mailbox"), name)

}

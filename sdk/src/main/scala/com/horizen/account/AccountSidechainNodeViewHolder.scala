package com.horizen.account

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.{AccountState, MessageProcessor, MessageProcessorUtil}
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
import com.horizen.utils.SDKModifiersCache
import com.horizen.validation.{HistoryBlockValidator, SemanticBlockValidator}
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainSettings, SidechainTypes}
import scorex.util.ModifierId
import sparkz.core.ModifiersCache
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{SemanticallyFailedModification, SemanticallySuccessfulModifier}
import sparkz.core.utils.NetworkTimeProvider

import scala.util.{Failure, Success, Try}

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
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock](sidechainSettings, timeProvider) {

  override type HSTOR = AccountHistoryStorage
  override type HIS = AccountHistory
  override type MS = AccountState
  override type VL = AccountWallet
  override type MP = AccountMemoryPool

  protected def messageProcessors(params: NetworkParams): Seq[MessageProcessor] = {
      MessageProcessorUtil.getMessageProcessorSeq(params, customMessageProcessors)
  }

  override def semanticBlockValidators(params: NetworkParams): Seq[SemanticBlockValidator[AccountBlock]] = {
    ChainIdBlockSemanticValidator(params) +: super.semanticBlockValidators(params)
  }

  override def historyBlockValidators(params: NetworkParams): Seq[HistoryBlockValidator[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountHistoryStorage, AccountHistory]] = {
    BaseFeeBlockValidator() +: super.historyBlockValidators(params)
  }

  override def restoreState(): Option[(HIS, MS, VL, MP)] = for {
    history <- AccountHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
    state <- AccountState.restoreState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, timeProvider)
    wallet <- AccountWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, secretStorage)
    pool <- Some(AccountMemoryPool.emptyPool)
  } yield (history, state, wallet, pool)

  override def postStop(): Unit = {
    log.info("AccountSidechainNodeViewHolder actor is stopping...")
    super.postStop()
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- AccountState.createGenesisState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, timeProvider, genesisBlock)

      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getCurrentConsensusEpochInfo)

      history <- AccountHistory.createGenesisHistory(historyStorage, consensusDataStorage, params, genesisBlock, semanticBlockValidators(params),
        historyBlockValidators(params), StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake))

      wallet <- AccountWallet.createGenesisWallet(sidechainSettings.wallet.seed.getBytes, secretStorage)

      pool <- Success(AccountMemoryPool.emptyPool)
    } yield (history, state, wallet, pool)

    result.get
  }

  // TODO: define SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(f) / ApplyFunctionOnNodeView / ApplyBiFunctionOnNodeView processors

  // Check if the next modifier will change Consensus Epoch, so notify History with current info.
  // Note: there is no need to store any info in the Wallet, since for Account model Forger is able
  // to get all necessary information from the State.
  override protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: AccountBlock): (HIS, VL) = {
     val historyAfterConsensusInfoApply = if (state.isSwitchingConsensusEpoch(modToApply)) {
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

  // Scan modifier only, there is no need to notify AccountWallet about fees,
  // since account balances are tracked only in the AccountState.
  // TODO: do we need to notify History with fee payments info?
  override protected def scanBlockWithFeePayments(history: HIS, state: MS, wallet: VL, modToApply: AccountBlock): (HIS, VL) = {
    (history, wallet.scanPersistent(modToApply))
  }

  override protected def getCurrentSidechainNodeViewInfo: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView[
      AccountTransaction[Proposition, Proof[Proposition]],
      AccountBlockHeader,
      AccountBlock,
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
      AccountTransaction[Proposition, Proof[Proposition]],
      AccountBlockHeader,
      AccountBlock,
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
      AccountTransaction[Proposition, Proof[Proposition]],
      AccountBlockHeader,
      AccountBlock,
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

  // TODO FOR MERGE
  override val listOfStorageInfo: Seq[SidechainStorageInfo] = Seq()


  /**
   * Cache for modifiers. If modifiers are coming out-of-order, they are to be stored in this cache.
   */
  protected override lazy val modifiersCache: ModifiersCache[AccountBlock, HIS] =
    new SDKModifiersCache[AccountBlock, HIS](sparksSettings.network.maxModifiersCacheSize)

  // TODO FOR MERGE: implement these methods
  override def dumpStorages: Unit = ???
  override def getStorageVersions: Map[String, String] = ???

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

  // Apply state and wallet with blocks one by one, if consensus epoch is going to be changed -> notify wallet and history.
  override protected def applyStateAndWallet(history: HIS,
                                    stateToApply: MS,
                                    walletToApply: VL,
                                    suffixTrimmed: IndexedSeq[AccountBlock],
                                    progressInfo: ProgressInfo[AccountBlock]): Try[SidechainNodeUpdateInformation] = Try {
    // TODO FOR MERGE - check Sidechain implementation and possibly bring this method in abstract class

    val updateInfoSample = SidechainNodeUpdateInformation(history, stateToApply, walletToApply, None, None, suffixTrimmed)
    progressInfo.toApply.foldLeft(updateInfoSample) { case (updateInfo, modToApply) =>
      if (updateInfo.failedMod.isEmpty) {
        val (newHistory, newWallet) = applyConsensusEpochInfo(updateInfo.history, updateInfo.state, updateInfo.wallet, modToApply)

        updateInfo.state.applyModifier(modToApply) match {
          case Success(stateAfterApply) =>
            val historyAfterApply = newHistory.reportModifierIsValid(modToApply).get
            context.system.eventStream.publish(SemanticallySuccessfulModifier(modToApply))

            val (historyAfterUpdateFee, walletAfterApply) = scanBlockWithFeePayments(historyAfterApply, stateAfterApply, newWallet, modToApply)
            SidechainNodeUpdateInformation(historyAfterUpdateFee, stateAfterApply, walletAfterApply, None, None, updateInfo.suffix :+ modToApply)

          case Failure(e) =>
            log.error(s"Failed to apply block ${modToApply.id} to the state.", e)
            val (historyAfterApply, newProgressInfo) = newHistory.reportModifierIsInvalid(modToApply, progressInfo).get
            context.system.eventStream.publish(SemanticallyFailedModification(modToApply, e))
            SidechainNodeUpdateInformation(historyAfterApply, updateInfo.state, newWallet, Some(modToApply), Some(newProgressInfo), updateInfo.suffix)
        }
      } else updateInfo
    }
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
      consensusDataStorage, stateMetadataStorage, stateDbStorage, customMessageProcessors, secretStorage, genesisBlock))

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

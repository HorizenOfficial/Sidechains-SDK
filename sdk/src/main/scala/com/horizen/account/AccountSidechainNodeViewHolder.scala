package com.horizen.account

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.{AccountState, EoaMessageProcessor, ForgerStakeMsgProcessor, MessageProcessor, WithdrawalMsgProcessor}
import com.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import com.horizen.account.transaction.AccountTransaction
import com.horizen.account.wallet.AccountWallet
import com.horizen.consensus._
import com.horizen.evm.Database
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.storage.SidechainSecretStorage
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainSettings, SidechainTypes}
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ModifierId

import scala.util.Success

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
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock](sidechainSettings, params, timeProvider) {

  override type HSTOR = AccountHistoryStorage
  override type HIS = AccountHistory
  override type MS = AccountState
  override type VL = AccountWallet
  override type MP = AccountMemoryPool

  protected def messageProcessors(params: NetworkParams): Seq[MessageProcessor] = {
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),

    ) ++ customMessageProcessors
  }

  override def restoreState(): Option[(HIS, MS, VL, MP)] = for {
    history <- AccountHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
    state <- AccountState.restoreState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params)
    wallet <- AccountWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, secretStorage)
    pool <- Some(AccountMemoryPool.emptyPool)
  } yield (history, state, wallet, pool)

  override protected def genesisState: (HIS, MS, VL, MP) = {
    val result = for {
      state <- AccountState.createGenesisState(stateMetadataStorage, stateDbStorage, messageProcessors(params), params, genesisBlock)

      (_: ModifierId, consensusEpochInfo: ConsensusEpochInfo) <- Success(state.getConsensusEpochInfo)

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
      val (lastBlockInEpoch: ModifierId, consensusEpochInfo: ConsensusEpochInfo) = state.getConsensusEpochInfo
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
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView[
      AccountTransaction[Proposition, Proof[Proposition]],
      AccountBlockHeader,
      AccountBlock,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView,
      _] =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView(f) => try {
          val l: AccountNodeView = new AccountNodeView(history(), minimalState(), vault(), memoryPool())
          sender() ! f(l)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }

      }
  }

  override  protected def applyFunctionOnNodeView: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[
      AccountTransaction[Proposition, Proof[Proposition]],
      AccountBlockHeader,
      AccountBlock,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView,
      _] =>
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
      T,A] =>
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

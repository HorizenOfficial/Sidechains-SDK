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
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ModifierId

import scala.util.Success

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
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock](sidechainSettings, params, timeProvider) {
  override type HSTOR = SidechainHistoryStorage
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  override def restoreState(): Option[(HIS, MS, VL, MP)] = for {
    history <- SidechainHistory.restoreHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators(params), historyBlockValidators(params))
    state <- SidechainState.restoreState(stateStorage, forgerBoxStorage, utxoMerkleTreeStorage, params, applicationState)
    wallet <- SidechainWallet.restoreWallet(sidechainSettings.wallet.seed.getBytes, walletBoxStorage, secretStorage,
      walletTransactionStorage, forgingBoxesInfoStorage, cswDataStorage, params, applicationWallet)
    pool <- Some(SidechainMemoryPool.emptyPool)
  } yield (history, state, wallet, pool)

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


override  protected def applyFunctionOnNodeView: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[
      SidechainTypes#SCBT,
      SidechainBlockHeader,
      SidechainBlock,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      _] =>
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
      _,_] =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView(f,functionParams) => try {
          val l: SidechainNodeView = new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), applicationState, applicationWallet)
          sender() ! f(l,functionParams)
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

  override protected def getCurrentSidechainNodeViewInfo: Receive = {
    case msg: AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView[
      SidechainTypes#SCBT,
      SidechainBlockHeader,
      SidechainBlock,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      _] =>
      msg match {
        case AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView(f) => try {
          val l: SidechainNodeView = new SidechainNodeView(history(), minimalState(), vault(), memoryPool(), applicationState, applicationWallet)
          sender() ! f(l)
        }
        catch {
          case e: Exception => sender() ! akka.actor.Status.Failure(e)
        }

      }
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


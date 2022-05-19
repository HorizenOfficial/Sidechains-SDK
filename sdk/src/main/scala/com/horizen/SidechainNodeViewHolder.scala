package com.horizen


import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import com.horizen.chain.FeePaymentsInfo
import com.horizen.consensus._
import com.horizen.node.SidechainNodeView
import com.horizen.params.NetworkParams
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.validation._
import com.horizen.wallet.ApplicationWallet
import scorex.core.NodeViewHolder.DownloadRequest
import scorex.core.consensus.History.ProgressInfo
import scorex.core.idToVersion
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages._
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.{ModifierId, ScorexLogging}

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
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCBT, SidechainBlock](sidechainSettings, params, timeProvider)
{
  override type SI = SidechainSyncInfo
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

  override def receive: Receive = {
    applyFunctionOnNodeView orElse
      applyBiFunctionOnNodeView orElse
      getCurrentSidechainNodeViewInfo orElse
      super.receive
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

  // Check if the next modifier will change Consensus Epoch, so notify History and Wallet with current info.
  override protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: SidechainBlock): (HIS, VL) = {
    if(state.isSwitchingConsensusEpoch(modToApply)) {
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
    if(state.isWithdrawalEpochLastIndex) {
      val feePayments = state.getFeePayments(stateWithdrawalEpochNumber)
      val historyAfterUpdateFee = history.updateFeePaymentsInfo(modToApply.id, FeePaymentsInfo(feePayments))

      val walletAfterApply: VL = wallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, feePayments, Some(state))

      (historyAfterUpdateFee, walletAfterApply)
    } else {
      val walletAfterApply: VL = wallet.scanPersistent(modToApply, stateWithdrawalEpochNumber, Seq(), None)
      (history, walletAfterApply)
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

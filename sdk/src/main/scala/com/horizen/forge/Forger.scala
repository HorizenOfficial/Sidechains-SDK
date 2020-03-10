package com.horizen.forge

import java.time.Instant
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.{ForgerDataWithSecrets, _}
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.NoncedBox
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.SidechainTransaction
import com.horizen.vrf.VRFProof
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.block.Block
import scorex.util.{ModifierId, ScorexLogging}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}


case class ForgingInfo(parentId: Block.BlockId,
                       timestamp: Block.Timestamp,
                       mainchainBlockRefToInclude: Seq[MainchainBlockReference],
                       txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
                       ownerPrivateKey: PrivateKey25519)


class Forger(settings: SidechainSettings,
             sidechainNodeViewHolderRef: ActorRef,
             mainchainSynchronizer: MainchainSynchronizer,
             companion: SidechainTransactionsCompanion,
             val params: NetworkParams) extends Actor with ScorexLogging with TimeToEpochSlotConverter {

  import com.horizen.forge.Forger._
  import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  @volatile private var forgingIsActive: Boolean = true

  val consensusMillisecondsInSlot: Int = params.consensusSecondsInSlot * 1000
  val forgingInitiator: Runnable = () => {
    while (forgingIsActive) {
      val currentTime: Long = Instant.now.getEpochSecond
      self ! Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot(timeStampToEpochNumber(currentTime), timeStampToSlotNumber(currentTime))
    }

    Thread.sleep(consensusMillisecondsInSlot)
  }

  ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor).execute(forgingInitiator)

  override def receive: Receive = {
    processStartForgingMessage orElse processStopForgingMessage orElse processTryForgeNextBlockForEpochAndSlotMessage orElse {
      case message: Any => log.error("Forger received strange message: " + message)
    }
  }


  protected def processStartForgingMessage: Receive = {
    case Forger.ReceivableMessages.StartForging => {
      forgingIsActive = true
      sender() ! Success()
    }
  }

  protected def processStopForgingMessage: Receive = {
    case Forger.ReceivableMessages.StopForging => {
      forgingIsActive = false
      sender() ! Success()
    }
  }

  protected def processTryForgeNextBlockForEpochAndSlotMessage: Receive = {
    case Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot(consensusEpochNumber, consensusSlotNumber) => {
      val forgingFunctionForEpochAndSlot: View => Option[SidechainBlock] =
        tryToForgeNextBlock(intToConsensusEpochNumber(consensusEpochNumber), intToConsensusSlotNumber(consensusSlotNumber))

      val forgeMessage =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Option[SidechainBlock]](forgingFunctionForEpochAndSlot)

      val forgedBlockAsFuture = sidechainNodeViewHolderRef ? forgeMessage

      forgedBlockAsFuture.onComplete{
        case Success(Some(newBlock: SidechainBlock)) => {
          sidechainNodeViewHolderRef ! LocallyGeneratedModifier[SidechainBlock](newBlock) //return block without applying? is it caller responsibility of applying new created block?
          sender() ! Success(newBlock.id)
        }

        case Success(_) => sender() ! Success(None) //no eligible forger box is present, just skip slot

        case Failure(exception) => sender() ! Failure(exception) // got some error during forging
      }
    }
  }


  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber)(view: View): Option[SidechainBlock] = {
    val bestBlockId = view.history.bestBlockId
    val bestBlockInfo = view.history.bestBlockInfo
    val nextBockTimestamp = getTimeStampForEpochAndSlot(nextConsensusEpochNumber, nextConsensusSlotNumber)
    require(nextBockTimestamp > bestBlockInfo.timestamp)

    val consensusInfo: FullConsensusEpochInfo = view.history.getFullConsensusEpochInfoForNextBlock(bestBlockId, nextConsensusEpochNumber)
    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)

    val availableForgersDataWithSecret: Seq[ForgerDataWithSecrets] = view.vault.getForgingDataWithSecrets(nextConsensusEpochNumber).getOrElse(Seq())

    val newBlockOpt = availableForgersDataWithSecret
      .toStream
      .map(forgerDataWithSecrets => (forgerDataWithSecrets, forgerDataWithSecrets.vrfSecret.prove(vrfMessage)))
      .find{case (forgerDataWithSecrets, vrfProof) =>
        vrfProofCheckAgainstStake(forgerDataWithSecrets.forgerBox.value(), vrfProof, totalStake)
      }
      .map{case (forgerDataWithSecrets, vrfProof) =>
        forgeBlock(view, bestBlockId, nextBockTimestamp, forgerDataWithSecrets, vrfProof)
      }

    newBlockOpt
  }

  protected def forgeBlock(view: View, parentBlockId: ModifierId, timestamp: Long, forgerDataWithSecrets: ForgerDataWithSecrets, vrfProof: VRFProof): SidechainBlock = {
    var withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength - view.history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex
    if(withdrawalEpochMcBlocksLeft == 0) // current best block is the last block of the epoch
      withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

    val mainchainBlockRefToInclude: Seq[MainchainBlockReference] = mainchainSynchronizer.getNewMainchainBlockReferences(
      view.history,
      Math.min(SidechainBlock.MAX_MC_BLOCKS_NUMBER, withdrawalEpochMcBlocksLeft) // to not to include mcblock references from different withdrawal epochs
    )

    val txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] =
      if(mainchainBlockRefToInclude.size == withdrawalEpochMcBlocksLeft) { // SC block is going to become the last block of the withdrawal epoch
        Seq() // no SC Txs allowed
      } else { // SC block is in the middle of the epoch
        view.pool.take(SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER) // TO DO: problems with types
          .map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
          .toSeq
      }

    SidechainBlock.create(
      parentBlockId,
      timestamp,
      mainchainBlockRefToInclude,
      txsToInclude,
      forgerDataWithSecrets.forgerBoxRewardPrivateKey,
      forgerDataWithSecrets.forgerBox,
      vrfProof,
      forgerDataWithSecrets.merklePath,
      companion,
      params
    ).get
  }
}

object Forger extends ScorexLogging {
  object ReceivableMessages {

    case object StartForging
    case object StopForging
    case class TryForgeNextBlockForEpochAndSlot(consensusEpochNumber: Int, consensusSlotNumber: Int)
  }

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
}

object ForgerRef {
  def props(settings: SidechainSettings, viewHolderRef: ActorRef, mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion, params: NetworkParams): Props =
    Props(new Forger(settings, viewHolderRef, mainchainSynchronizer, companion, params))

  def apply(settings: SidechainSettings, viewHolderRef: ActorRef, mainchainSynchronizer: MainchainSynchronizer, companion: SidechainTransactionsCompanion, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, params))

  def apply(name: String, settings: SidechainSettings, viewHolderRef: ActorRef, mainchainSynchronizer: MainchainSynchronizer, companion: SidechainTransactionsCompanion, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, params), name)
}






package com.horizen.forge

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.NoncedBox
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.forge.Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.forge.Forger.SendMessages.{ForgeFailed, ForgeResult, ForgeSuccess, SkipSlot}
import com.horizen.forge.Forger.View
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.SidechainTransaction
import com.horizen.vrf.VRFProof
import com.horizen.{ForgerDataWithSecrets, _}
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.block.Block
import scorex.util.{ModifierId, ScorexLogging}

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

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  override def receive: Receive = {
    processTryForgeNextBlockForEpochAndSlotMessage orElse {
      case message: Any => log.error(s"Forger received strange message: ${message} from ${sender().path.name}")
    }
  }

  protected def processTryForgeNextBlockForEpochAndSlotMessage: Receive = {
    case TryForgeNextBlockForEpochAndSlot(consensusEpochNumber, consensusSlotNumber) => {
      val forgingFunctionForEpochAndSlot: View => ForgeResult = tryToForgeNextBlock(consensusEpochNumber, consensusSlotNumber)

      val forgeMessage =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult](forgingFunctionForEpochAndSlot)

      val forgeMessageSender: ActorRef = sender()

      val forgedBlockAsFuture = sidechainNodeViewHolderRef ? forgeMessage
      forgedBlockAsFuture.onComplete{
        case Success(forgeResult) => forgeMessageSender ! forgeResult //caller is responsible for applying new created block if required
        case Failure(exception) => forgeMessageSender ! ForgeFailed(exception) //got some error during future completing
      }
    }
  }

  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber)(view: View): ForgeResult = {
    log.info(s"Try to forge block for epoch ${nextConsensusEpochNumber} with slot ${nextConsensusSlotNumber}")
    val bestBlockId = view.history.bestBlockId
    val bestBlockInfo = view.history.bestBlockInfo
    val bestBlockEpochAndSlot = timestampToEpochAndSlot(bestBlockInfo.timestamp)

    val nextBockTimestamp = getTimeStampForEpochAndSlot(nextConsensusEpochNumber, nextConsensusSlotNumber)
    val nextBlockEpochAndSlot: ConsensusEpochAndSlot = ConsensusEpochAndSlot(nextConsensusEpochNumber, nextConsensusSlotNumber)
    if(bestBlockEpochAndSlot >= nextBlockEpochAndSlot) {
      ForgeFailed(new IllegalArgumentException (s"Try to forge block with epochAndSlot ${nextBlockEpochAndSlot} but current best block epochAndSlot are: ${bestBlockEpochAndSlot}"))
    }

    if ((nextConsensusEpochNumber - timeStampToEpochNumber(bestBlockInfo.timestamp)) > 1) log.warn("Forging is not possible: whole consensus epoch(s) are missed")

    val consensusInfo: FullConsensusEpochInfo = view.history.getFullConsensusEpochInfoForNextBlock(bestBlockId, nextConsensusEpochNumber)
    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)

    val availableForgersDataWithSecret: Seq[ForgerDataWithSecrets] = view.vault.getForgingDataWithSecrets(nextConsensusEpochNumber).getOrElse(Seq())

    val forgingDataOpt: Option[(ForgerDataWithSecrets, VRFProof)] = availableForgersDataWithSecret
      .toStream
      .map(forgerDataWithSecrets => (forgerDataWithSecrets, forgerDataWithSecrets.vrfSecret.prove(vrfMessage))) //get secrets thus filter forger boxes not owned by node
      .find{case (forgerDataWithSecrets, vrfProof) => vrfProofCheckAgainstStake(forgerDataWithSecrets.forgerBox.value(), vrfProof, totalStake)} //check our forger boxes against stake

    val forgingResult = forgingDataOpt
                                      .map{case (forgerDataWithSecrets, vrfProof) => forgeBlock(view, bestBlockId, nextBockTimestamp, forgerDataWithSecrets, vrfProof)}
                                      .getOrElse(SkipSlot)

    log.info(s"Forge result is: ${forgingResult}")
    forgingResult
  }

  protected def forgeBlock(view: View, parentBlockId: ModifierId, timestamp: Long, forgerDataWithSecrets: ForgerDataWithSecrets, vrfProof: VRFProof): ForgeResult = {
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

    val blockCreationResult = SidechainBlock.create(
                                                      parentBlockId,
                                                      timestamp,
                                                      mainchainBlockRefToInclude,
                                                      txsToInclude,
                                                      forgerDataWithSecrets.forgerBoxRewardPrivateKey,
                                                      forgerDataWithSecrets.forgerBox,
                                                      vrfProof,
                                                      forgerDataWithSecrets.merklePath,
                                                      companion,
                                                      params)

    blockCreationResult match {
      case Success(block) => ForgeSuccess(block)
      case Failure(exception) => ForgeFailed(exception)
    }
  }
}

object Forger extends ScorexLogging {
  object ReceivableMessages {
    case class TryForgeNextBlockForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber)
  }

  object SendMessages {
    sealed trait ForgeResult

    case class ForgeSuccess(block: SidechainBlock) extends ForgeResult {override def toString: String = s"Successfully generated block ${block.id.toString}"}
    case object SkipSlot extends ForgeResult {override def toString: String = s"Skipped slot for forging"}
    case class ForgeFailed(ex: Throwable) extends ForgeResult {override def toString: String = s"Failed block generation due ${ex}"}
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






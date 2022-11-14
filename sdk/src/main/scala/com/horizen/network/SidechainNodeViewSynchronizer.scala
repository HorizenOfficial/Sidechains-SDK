package com.horizen.network


import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.network.SidechainNodeViewSynchronizer.ReceivableMessages.ModifiersIgnoredForReindex
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import sparkz.core.network.{ConnectedPeer,  NodeViewSynchronizer}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SyntacticallyFailedModification}
import sparkz.core.network.message.{InvData, ModifiersData}
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.NetworkSettings
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifierTypeId, NodeViewModifier}
import sparkz.core.network.ModifiersStatus._
import scala.concurrent.ExecutionContext

class SidechainNodeViewSynchronizer(networkControllerRef: ActorRef,
                                    viewHolderRef: ActorRef,
                                    syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                    networkSettings: NetworkSettings,
                                    timeProvider: NetworkTimeProvider,
                                    modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    SidechainBlock, SidechainHistory, SidechainMemoryPool](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers){

  protected var isReindexing : Boolean = false

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ModifiersIgnoredForReindex])
    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("SidechainNodeViewSynchronizer actor is stopping...")
    super.postStop()
  }

  private val onSyntacticallyFailedModifier: Receive = {
    case SyntacticallyFailedModification(mod, exception) =>
      exception match {
        case _: BlockInFutureException =>
          // When next time NodeViewSynchronizer.processInv will be emitted for mod.id it will be processed again.
          // So no ban for mod.id
          deliveryTracker.setUnknown(mod.id)
        case _: InconsistentDataException =>
          // Try to ban the sender only (in case of modifier from remote)
          val peerOpt = deliveryTracker.peerInfo(mod.id)
          deliveryTracker.setUnknown(mod.id)
          peerOpt.foreach(penalizeMisbehavingPeer)
        case _ => // InvalidBlockException, InvalidSidechainBlockHeaderException and all other exceptions
          // Ban both mod.id and peer
          deliveryTracker.setInvalid(mod.id).foreach(penalizeMisbehavingPeer)
      }
  }

  /**
   * Object ids coming from other node.
   * Filter out modifier ids that are already in process (requested, received or applied),
   * request unknown ids from peer and set this ids to requested state.
   */
  override protected def processInv(invData: InvData, peer: ConnectedPeer): Unit = {
    if (this.isReindexing){
      log.warn("Got data from peer while reindexing - will be discarded")
    }else {
      super.processInv(invData, peer)
    }
  }


  override protected def modifiersFromRemote(data: ModifiersData, remote: ConnectedPeer): Unit = {
    val typeId = data.typeId
    val modifiers = data.modifiers
    if (isReindexing) {
      log.info(s"Got ${modifiers.size} modifiers of type $typeId from remote connected peer: $remote while reindexing - wil be discarded" )
      modifiers.keySet.foreach(id =>
        if (deliveryTracker.status(id) != Unknown){
          //we are receiving a modifier previously requested, but in the meantime we started to reindex: we ingore
          //it and set the delivery to unknown, so it will be cleared from the delivery cache
          deliveryTracker.setUnknown(id)
        }
      )
    }else{
      super.modifiersFromRemote(data,remote)
    }
  }


  protected def changedHistoryEvent: Receive = {
    case ChangedHistory(sHistory: SidechainHistory) =>
      historyReaderOpt = Some(sHistory)
      isReindexing = sHistory.isReindexing()
  }

  protected def onModifierIgnoredForReindex: Receive = {
    case ModifiersIgnoredForReindex(ids: Seq[scorex.util.ModifierId]) =>
      ids.foreach(id => deliveryTracker.setUnknown(id))

  }

  override protected def viewHolderEvents: Receive =
      onSyntacticallyFailedModifier orElse
      onModifierIgnoredForReindex orElse
      changedHistoryEvent orElse
      super.viewHolderEvents
}

object SidechainNodeViewSynchronizer {

  object ReceivableMessages {
    case class ModifiersIgnoredForReindex(ids: Seq[scorex.util.ModifierId])
  }

  def props(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])
           (implicit ex: ExecutionContext): Props =
    Props(new SidechainNodeViewSynchronizer(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings,
      timeProvider, modifierSerializers))

  def apply(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])
           (implicit context: ActorRefFactory, ex: ExecutionContext): ActorRef =
    context.actorOf(props(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers))

  def apply(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]],
            name: String)
           (implicit context: ActorRefFactory, ex: ExecutionContext): ActorRef =
    context.actorOf(props(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers), name)
}


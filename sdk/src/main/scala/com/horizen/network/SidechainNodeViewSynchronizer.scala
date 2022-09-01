package com.horizen.network

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import sparkz.core.network.NodeViewSynchronizer
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.NetworkSettings
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext

class SidechainNodeViewSynchronizer(networkControllerRef: ActorRef,
                                    viewHolderRef: ActorRef,
                                    syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                    networkSettings: NetworkSettings,
                                    timeProvider: NetworkTimeProvider,
                                    modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    SidechainBlock, SidechainHistory, SidechainMemoryPool](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers){

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

  override protected def viewHolderEvents: Receive =
    onSyntacticallyFailedModifier orElse
      super.viewHolderEvents
}



object SidechainNodeViewSynchronizer {
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
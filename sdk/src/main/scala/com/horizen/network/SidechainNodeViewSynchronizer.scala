package com.horizen.network

import java.nio.ByteBuffer

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import scorex.core.NodeViewHolder.ReceivableMessages.{ModifiersFromRemote, TransactionsFromRemote}
import scorex.core.network.ModifiersStatus.Requested
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.{ConnectedPeer, NodeViewSynchronizer}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import scorex.core.network.message.{ModifiersData, ModifiersSpec}
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.NetworkSettings
import scorex.core.transaction.Transaction
import scorex.core.utils.NetworkTimeProvider
import scorex.core.validation.MalformedModifierError
import scorex.core.{ModifierId, ModifierTypeId, NodeViewModifier}
import scorex.util.serialization.VLQReader
import scorex.util.serialization.VLQByteBufferReader

import scala.concurrent.ExecutionContext
import scala.util.Failure

class SidechainNodeViewSynchronizer(networkControllerRef: ActorRef,
                                    viewHolderRef: ActorRef,
                                    syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                    networkSettings: NetworkSettings,
                                    timeProvider: NetworkTimeProvider,
                                    modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    SidechainBlock, SidechainHistory, SidechainMemoryPool](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers){

  override protected val deliveryTracker = new SidechainDeliveryTracker(context.system, deliveryTimeout, maxDeliveryChecks, self)

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
   * Parse modifiers using specified serializer, check that its id is equal to the declared one,
   * penalize misbehaving peer for every incorrect modifier or additional bytes after the modifier,
   * call deliveryTracker.onReceive() for every correct modifier to update its status
   *
   * @return collection of parsed modifiers
   */
  private def parseModifiers[M <: NodeViewModifier](modifiers: Map[ModifierId, Array[Byte]],
                                                    serializer: ScorexSerializer[M],
                                                    remote: ConnectedPeer): Iterable[M] = {
    modifiers.flatMap { case (id, bytes) =>
      val reader: VLQReader = new VLQByteBufferReader(ByteBuffer.wrap(bytes))
      val mod = serializer.parse(reader)

      if (reader.remaining != 0) {
        penalizeMisbehavingPeer(remote)
        log.warn(s"Received additianl bytes after block. Declared id ${encoder.encodeId(id)} from ${remote.toString}")
      }

      if (id == mod.id) {
        Some(mod)
      } else {
        // Penalize peer and do nothing - it will be switched to correct state on CheckDelivery
        penalizeMisbehavingPeer(remote)
        log.warn(s"Failed to parse modifier with declared id ${encoder.encodeId(id)} from ${remote.toString}")
        None
      }
    }
  }

  /**
   * This function is copied from scorex for catching DataFromPeer event
   *
   * Logic to process modifiers got from another peer.
   * Filter out non-requested modifiers (with a penalty to spamming peer),
   * parse modifiers and send valid modifiers to NodeViewHolder
   */
  @Override
  override protected def modifiersFromRemote: Receive = {
    case DataFromPeer(spec, data: ModifiersData@unchecked, remote)
      if spec.messageCode == ModifiersSpec.MessageCode =>

      val typeId = data.typeId
      val modifiers = data.modifiers
      log.info(s"Got ${modifiers.size} modifiers of type $typeId from remote connected peer: $remote")
      log.trace(s"Received modifier ids ${modifiers.keySet.map(encoder.encodeId).mkString(",")}")

      // filter out non-requested modifiers
      val requestedModifiers = processSpam(remote, typeId, modifiers)

      modifierSerializers.get(typeId) match {
        case Some(serializer: ScorexSerializer[SidechainTypes#SCBT]@unchecked) if typeId == Transaction.ModifierTypeId =>
          // parse all transactions and send them to node view holder
          val parsed: Iterable[SidechainTypes#SCBT] = parseModifiers(requestedModifiers, serializer, remote)
          viewHolderRef ! TransactionsFromRemote(parsed)

        case Some(serializer: ScorexSerializer[SidechainBlock]@unchecked) =>
          // parse all modifiers and put them to modifiers cache
          val parsed: Iterable[SidechainBlock] = parseModifiers(requestedModifiers, serializer, remote)
          val valid: Iterable[SidechainBlock] = parsed.filter(validateAndSetStatus(remote, _))
          if (valid.nonEmpty) viewHolderRef ! ModifiersFromRemote[SidechainBlock](valid)

        case _ =>
          log.error(s"Undefined serializer for modifier of type $typeId")
      }
  }

  /**
   * This function is copied from scorex for using in modifiersFromRemote function
   *
   * Move `pmod` to `Invalid` if it is permanently invalid, to `Received` otherwise
   */
  @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
  private def validateAndSetStatus(remote: ConnectedPeer, pmod: SidechainBlock): Boolean = {
    historyReaderOpt match {
      case Some(hr) =>
        hr.applicableTry(pmod) match {
          case Failure(e) if e.isInstanceOf[MalformedModifierError] =>
            log.warn(s"Modifier ${pmod.encodedId} is permanently invalid", e)
            deliveryTracker.setInvalid(pmod.id)
            penalizeMisbehavingPeer(remote)
            false
          case _ =>
            deliveryTracker.setReceived(pmod.id, remote)
            true
        }
      case None =>
        log.error("Got modifier while history reader is not ready")
        deliveryTracker.setReceived(pmod.id, remote)
        true
    }
  }

  /**
   * This function is copied from scorex for using in modifiersFromRemote function
   *
   * Get modifiers from remote peer,
   * filter out spam modifiers and penalize peer for spam
   *
   * @return ids and bytes of modifiers that were requested by our node
   */
  private def processSpam(remote: ConnectedPeer,
                          typeId: ModifierTypeId,
                          modifiers: Map[ModifierId, Array[Byte]]): Map[ModifierId, Array[Byte]] = {

    val (requested, spam) = modifiers.partition { case (id, _) =>
      deliveryTracker.status(id) == Requested
    }

    if (spam.nonEmpty) {
      log.info(s"Spam attempt: peer $remote has sent a non-requested modifiers of type $typeId with ids" +
        s": ${spam.keys.map(encoder.encodeId)}")
      penalizeSpammingPeer(remote)
    }
    requested
  }

  override protected def viewHolderEvents: Receive = {onSyntacticallyFailedModifier orElse
    modifiersFromRemote orElse
    super.viewHolderEvents}

}



object SidechainNodeViewSynchronizer {
  def props(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])
           (implicit ex: ExecutionContext): Props =
    Props(new SidechainNodeViewSynchronizer(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings,
      timeProvider, modifierSerializers))

  def apply(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])
           (implicit context: ActorRefFactory, ex: ExecutionContext): ActorRef =
    context.actorOf(props(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers))

  def apply(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]],
            name: String)
           (implicit context: ActorRefFactory, ex: ExecutionContext): ActorRef =
    context.actorOf(props(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers), name)
}
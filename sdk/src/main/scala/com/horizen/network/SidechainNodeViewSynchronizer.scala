package com.horizen.network

import java.nio.ByteBuffer

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import scorex.util.serialization.{VLQByteBufferReader, VLQReader}
import sparkz.core.NodeViewHolder.ReceivableMessages.{ModifiersFromRemote, TransactionsFromRemote}
import sparkz.core.network.ModifiersStatus.Requested
import sparkz.core.network.{ConnectedPeer, ModifiersStatus, NodeViewSynchronizer}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SyntacticallyFailedModification}
import sparkz.core.network.message.{InvData, Message, ModifiersData}
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.NetworkSettings
import sparkz.core.transaction.Transaction
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.validation.MalformedModifierError
import sparkz.core.{ModifierId, ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class SidechainNodeViewSynchronizer(networkControllerRef: ActorRef,
                                    viewHolderRef: ActorRef,
                                    syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                    networkSettings: NetworkSettings,
                                    timeProvider: NetworkTimeProvider,
                                    modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    SidechainBlock, SidechainHistory, SidechainMemoryPool](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers){

  protected var isReindexing : Boolean = false

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
    if (this.isReindexing == true){
      log.warn("Got data from peer while reindexing - will be discarded")
    }else {
      (mempoolReaderOpt, historyReaderOpt) match {
        case (Some(mempool), Some(history)) =>
          val modifierTypeId = invData.typeId
          val newModifierIds = (modifierTypeId match {
            case Transaction.ModifierTypeId =>
              invData.ids.filter(mid => deliveryTracker.status(mid, mempool) == ModifiersStatus.Unknown)
            case _ =>
              invData.ids.filter(mid => deliveryTracker.status(mid, history) == ModifiersStatus.Unknown)
          })
            .take(deliveryTracker.getPeerLimit(peer))

          if (newModifierIds.nonEmpty) {
            val msg = Message(requestModifierSpec, Right(InvData(modifierTypeId, newModifierIds)), None)
            peer.handlerRef ! msg
            deliveryTracker.setRequested(newModifierIds, modifierTypeId, peer)
          }

        case _ =>
          log.warn(s"Got data from peer while readers are not ready ${(mempoolReaderOpt, historyReaderOpt)}")
      }
    }
  }



  /**
   * Logic to process modifiers got from another peer.
   * Filter out non-requested modifiers (with a penalty to spamming peer),
   * parse modifiers and send valid modifiers to NodeViewHolder
   */
  override protected def modifiersFromRemote(data: ModifiersData, remote: ConnectedPeer): Unit = {
    val typeId = data.typeId
    val modifiers = data.modifiers
    log.info(s"Got ${modifiers.size} modifiers of type $typeId from remote connected peer: $remote")
    log.trace(s"Received modifier ids ${modifiers.keySet.map(encoder.encodeId).mkString(",")}")

    // filter out non-requested modifiers
    val requestedModifiers = processSpam(remote, typeId, modifiers)

    modifierSerializers.get(typeId) match {
      case Some(serializer: SparkzSerializer[SidechainTypes#SCBT]@unchecked) if typeId == Transaction.ModifierTypeId =>
        // parse all transactions and send them to node view holder
        val parsed: Iterable[SidechainTypes#SCBT] = parseModifiers(requestedModifiers, serializer, remote)
        viewHolderRef ! TransactionsFromRemote(parsed)

      case Some(serializer: SparkzSerializer[SidechainBlock]@unchecked) =>
        if (this.isReindexing){
          log.info("Got transaction while reindexing - will be discarded")
        }else {
          // parse all modifiers and put them to modifiers cache
          val parsed: Iterable[SidechainBlock] = parseModifiers(requestedModifiers, serializer, remote)
          val valid: Iterable[SidechainBlock] = parsed.filter(validateAndSetStatus(remote, _))
          if (valid.nonEmpty) viewHolderRef ! ModifiersFromRemote[SidechainBlock](valid)
        }

      case _ =>
        log.error(s"Undefined serializer for modifier of type $typeId")
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
                                                    serializer: SparkzSerializer[M],
                                                    remote: ConnectedPeer): Iterable[M] = {
    modifiers.flatMap { case (id, bytes) =>
      val reader: VLQReader = new VLQByteBufferReader(ByteBuffer.wrap(bytes))

      if (reader.remaining != 0) {
        penalizeMisbehavingPeer(remote)
        log.warn(s"Received additional bytes after block. Declared id ${encoder.encodeId(id)} from ${remote.toString}")
      }

      serializer.parseTry(reader) match {
        case Success(mod) if id == mod.id =>
          Some(mod)
        case _ =>
          // Penalize peer and do nothing - it will be switched to correct state on CheckDelivery
          penalizeMisbehavingPeer(remote)
          log.warn(s"Failed to parse modifier with declared id ${encoder.encodeId(id)} from ${remote.toString}")
          None
      }
    }
  }

  /**
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


  protected def changedHistoryEvent: Receive = {
    case ChangedHistory(sHistory: SidechainHistory) =>
      historyReaderOpt = Some(sHistory)
      if (!historyReaderOpt.isEmpty){
        isReindexing = sHistory.isReindexing()
      }
  }

  override protected def viewHolderEvents: Receive =
      onSyntacticallyFailedModifier orElse
      changedHistoryEvent orElse
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


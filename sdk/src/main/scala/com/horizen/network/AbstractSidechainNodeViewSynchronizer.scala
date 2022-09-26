package com.horizen.network

import akka.actor.ActorRef
import com.horizen._
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import scorex.core.consensus.{HistoryReader, SyncInfo}
import scorex.core.network.NodeViewSynchronizer
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import scorex.core.network.message.SyncInfoMessageSpec
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.NetworkSettings
import scorex.core.transaction.MempoolReader
import scorex.core.transaction.state.MinimalState
import scorex.core.utils.NetworkTimeProvider
import scorex.core.{ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

abstract class AbstractSidechainNodeViewSynchronizer[
  TX <: Transaction,
  //SI <: SyncInfo,
  //SIS <: SyncInfoMessageSpec[SI],
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  //HR <: HistoryReader[PMOD, SI],
  MR <: MempoolReader[TX]  : ClassTag,
  FPI <: AbstractFeePaymentsInfo,
  HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
  HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS] : ClassTag]
(
  networkControllerRef: ActorRef,
  viewHolderRef: ActorRef,
  syncInfoSpec: SidechainSyncInfoMessageSpec.type,
  networkSettings: NetworkSettings,
  timeProvider: NetworkTimeProvider,
  modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[TX, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    PMOD, HIS, MR](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers)
{
  override protected val deliveryTracker = new SidechainDeliveryTracker(context.system, deliveryTimeout, maxDeliveryChecks, self)

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

  override protected def viewHolderEvents: Receive = onSyntacticallyFailedModifier orElse super.viewHolderEvents
}




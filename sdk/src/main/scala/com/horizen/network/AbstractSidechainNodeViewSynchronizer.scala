package com.horizen.network

import akka.actor.ActorRef
import com.horizen._
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.history.AbstractHistory
import com.horizen.history.validation.{BlockInFutureException, InconsistentDataException}
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import sparkz.core.network.NodeViewSynchronizer
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.NetworkSettings
import sparkz.core.transaction.MempoolReader
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

abstract class AbstractSidechainNodeViewSynchronizer[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
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
  modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[TX, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    PMOD, HIS, MR](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers)
{
  override def postStop(): Unit = {
    log.info(s"${getClass.getSimpleName} actor is stopping...")
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




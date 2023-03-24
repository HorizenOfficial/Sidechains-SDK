package io.horizen.utxo.network

import akka.actor.{ActorRef, ActorRefFactory, Props}
import io.horizen._
import io.horizen.network.AbstractSidechainNodeViewSynchronizer
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.storage.SidechainHistoryStorage
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.NetworkSettings
import sparkz.core.transaction.MempoolReader
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext

class SidechainNodeViewSynchronizer(networkControllerRef: ActorRef,
                                    viewHolderRef: ActorRef,
                                    syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                    networkSettings: NetworkSettings,
                                    timeProvider: NetworkTimeProvider,
                                    modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends AbstractSidechainNodeViewSynchronizer[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock, MempoolReader[SidechainTypes#SCBT],
    SidechainFeePaymentsInfo,
    SidechainHistoryStorage, SidechainHistory](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers){
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
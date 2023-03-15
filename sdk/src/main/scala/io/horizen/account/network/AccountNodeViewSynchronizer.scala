package io.horizen.account.network

import akka.actor.{ActorRef, ActorRefFactory, Props}
import io.horizen._
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.history.AccountHistory
import io.horizen.account.storage.AccountHistoryStorage
import io.horizen.network.AbstractSidechainNodeViewSynchronizer
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.NetworkSettings
import sparkz.core.transaction.MempoolReader
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext

class AccountNodeViewSynchronizer(networkControllerRef: ActorRef,
                                  viewHolderRef: ActorRef,
                                  syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                  networkSettings: NetworkSettings,
                                  timeProvider: NetworkTimeProvider,
                                  modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
  extends AbstractSidechainNodeViewSynchronizer[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    MempoolReader[SidechainTypes#SCAT],
    AccountFeePaymentsInfo,
    AccountHistoryStorage,
    AccountHistory
  ](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers){
 }

object AccountNodeViewSynchronizer {
 def props(networkControllerRef: ActorRef,
           viewHolderRef: ActorRef,
           syncInfoSpec: SidechainSyncInfoMessageSpec.type,
           networkSettings: NetworkSettings,
           timeProvider: NetworkTimeProvider,
           modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]])
          (implicit ex: ExecutionContext): Props =
  Props(new AccountNodeViewSynchronizer(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings,
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


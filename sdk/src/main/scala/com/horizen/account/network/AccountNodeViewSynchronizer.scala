package com.horizen.account.network

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.horizen._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.history.AccountHistory
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.network.AbstractSidechainNodeViewSynchronizer
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.NetworkSettings
import scorex.core.transaction.MempoolReader
import scorex.core.utils.NetworkTimeProvider
import scorex.core.{ModifierTypeId, NodeViewModifier}

import scala.concurrent.ExecutionContext

class AccountNodeViewSynchronizer(networkControllerRef: ActorRef,
                                  viewHolderRef: ActorRef,
                                  syncInfoSpec: SidechainSyncInfoMessageSpec.type,
                                  networkSettings: NetworkSettings,
                                  timeProvider: NetworkTimeProvider,
                                  modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])(implicit ec: ExecutionContext)
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
           modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])
          (implicit ex: ExecutionContext): Props =
  Props(new AccountNodeViewSynchronizer(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings,
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


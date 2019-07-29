package com.horizen

import java.util.Optional

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import com.horizen.transaction.{RegularTransaction, Transaction, TransactionSerializer}
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.core.block.Block
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, SuccessfulTransaction}
import scorex.core.serialization.Serializer
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider

class SidechainNodeViewHolder(sdkSettings: SidechainSettings,
                              timeProvider: NetworkTimeProvider)
  extends scorex.core.NodeViewHolder[SidechainTypes#BT, SidechainBlock]
{
  import SidechainNodeViewHolder.ReceivableMessages._

  override type SI = scorex.core.consensus.SyncInfo

  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  override val scorexSettings: ScorexSettings = sdkSettings.scorexSettings;

  override def restoreState(): Option[(HIS, MS, VL, MP)] = ???

  override protected def genesisState: (HIS, MS, VL, MP) = ???

  // TO DO: Put it into NodeViewSynchronizerRef::modifierSerializers. Also put here map of custom sidechain transactions
  /*val customTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]] = ???

  override val modifierSerializers: Map[Byte, Serializer[_ <: NodeViewModifier]] =
    Map(new RegularTransaction().modifierTypeId() -> new SidechainTransactionsCompanion(customTransactionSerializers))
  */


 /* def getCurrentNodeView: SidechainNodeView = {
    new SidechainNodeView(history(), minimalState(), vault(), memoryPool())
  }*/


  protected def getCurrentSidechainNodeViewInfo: Receive = {
    case GetDataFromCurrentSidechainNodeView(f) => sender() ! f(new SidechainNodeView(history(), minimalState(), vault(), memoryPool()))
  }

  override def receive: Receive = getCurrentSidechainNodeViewInfo orElse super.receive
}

object SidechainNodeViewHolder /*extends ScorexLogging with ScorexEncoding*/ {
  def generateGenesisState(hybridSettings: SidechainSettings,
                           timeProvider: NetworkTimeProvider): Unit = ??? // TO DO: change later
  object ReceivableMessages{
    case class GetDataFromCurrentSidechainNodeView[HIS, MS, VL, MP, A](f: SidechainNodeView => A)
  }

}

object SidechainNodeViewHolderRef {
  def props(settings: SidechainSettings,
            timeProvider: NetworkTimeProvider): Props =
    Props(new SidechainNodeViewHolder(settings, timeProvider))

  def apply(settings: SidechainSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider))

  def apply(name: String,
            settings: SidechainSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider), name)
}
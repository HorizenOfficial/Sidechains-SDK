package com.horizen.account.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{MempoolReAddedTransactions, RemovedMempoolTransactions}
import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.EthereumTransaction
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedVault, SemanticallySuccessfulModifier, SuccessfulTransaction}
import sparkz.util.SparkzLogging

import scala.concurrent.ExecutionContext

class WebSocketAccountServer(wsPort: Int)
  extends Actor
  with SparkzLogging {
  val websocket = new WebSocketAccountServerImpl(wsPort, classOf[WebSocketAccountServerEndpoint]);

  try {
    websocket.start()
  } catch {
    case _: Throwable => log.error("Couldn't start websocket server!")
  }


  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[_]])
    context.system.eventStream.subscribe(self, classOf[SuccessfulTransaction[_]])
    context.system.eventStream.subscribe(self, classOf[ChangedVault[_]])
    context.system.eventStream.subscribe(self, classOf[MempoolReAddedTransactions[_]])
    context.system.eventStream.subscribe(self, classOf[RemovedMempoolTransactions[_]])
  }

  override def postStop(): Unit = {
    log.debug("WebSocket Server actor is stopping...")
    websocket.stop()
    super.postStop()
  }

  override def receive: Receive = {
    checkMessage orElse {
      case message: Any => log.error("WebsocketServer received strange message: " + message)
    }
  }

  protected def checkMessage: Receive = {
    case SemanticallySuccessfulModifier(block: AccountBlock) =>
      websocket.onSemanticallySuccessfulModifier(block)
    case SuccessfulTransaction(tx: EthereumTransaction) =>
      websocket.onSuccessfulTransaction(tx)
    case ChangedVault(_) =>
      websocket.onChangedVault()
    case MempoolReAddedTransactions(readdedTxs: Seq[EthereumTransaction]) =>
      websocket.onMempoolReaddedTransaction(readdedTxs)
    case RemovedMempoolTransactions(removedTxs: Seq[SidechainTypes#SCAT]) =>
      websocket.onRemovedMempoolTransactions(removedTxs)
  }
}

object WebSocketAccountServerRef {

  var sidechainNodeViewHolderRef: ActorRef = null

  def props(sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit ec: ExecutionContext): Props = {
    this.sidechainNodeViewHolderRef = sidechainNodeViewHolderRef
    Props(new WebSocketAccountServer(wsPort))
  }

  def apply(sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, wsPort))

  def apply(name: String, sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, wsPort), name)
}
package io.horizen.account.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import io.horizen.account.NewExecTransactionsEvent
import io.horizen.account.block.AccountBlock
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.network.SyncStatus
import io.horizen.network.SyncStatusActor.{NotifySyncStart, NotifySyncStop, NotifySyncUpdate}
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
    context.system.eventStream.subscribe(self, classOf[NotifySyncStart])
    context.system.eventStream.subscribe(self, classOf[NotifySyncStop])
    context.system.eventStream.subscribe(self, classOf[NotifySyncUpdate])
    context.system.eventStream.subscribe(self, classOf[NewExecTransactionsEvent])
  }

  override def postStop(): Unit = {
    log.debug("Websocket Server actor is stopping...")
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
    case NotifySyncStart(syncStatus: SyncStatus) =>
      websocket.onSyncStart(syncStatus)
    case NotifySyncStop() =>
      websocket.onSyncStop()
    case NotifySyncUpdate(syncStatus: SyncStatus) =>
      websocket.onSyncStart(syncStatus)
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

package io.horizen.account.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import io.horizen.SidechainTypes
import io.horizen.account.AccountSidechainNodeViewHolder.NewExecTransactionsEvent
import io.horizen.account.api.rpc.service.RpcProcessor
import io.horizen.account.block.AccountBlock
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.network.SyncStatus
import io.horizen.network.SyncStatusActor.{NotifySyncStart, NotifySyncStop, NotifySyncUpdate}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedVault, SemanticallySuccessfulModifier}
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
    context.system.eventStream.subscribe(self, classOf[ChangedVault[_]])
    context.system.eventStream.subscribe(self, classOf[NotifySyncStart])
    context.system.eventStream.subscribe(self, NotifySyncStop.getClass)
    context.system.eventStream.subscribe(self, classOf[NotifySyncUpdate])
    context.system.eventStream.subscribe(self, classOf[NewExecTransactionsEvent])
  }

  override def postStop(): Unit = {
    log.debug("Websocket Server actor is stopping...")
    websocket.stop()
    super.postStop()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.error("Websocket Server actor was restarted because of: ", reason)
    // Subscribe to events after actor restart
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[_]])
    context.system.eventStream.subscribe(self, classOf[ChangedVault[_]])
    context.system.eventStream.subscribe(self, classOf[NotifySyncStart])
    context.system.eventStream.subscribe(self, NotifySyncStop.getClass)
    context.system.eventStream.subscribe(self, classOf[NotifySyncUpdate])
    context.system.eventStream.subscribe(self, classOf[NewExecTransactionsEvent])
  }

  override def receive: Receive = {
    checkMessage orElse {
      case message: Any => log.error("WebsocketServer received strange message: " + message)
    }
  }

  protected def checkMessage: Receive = {
    case SemanticallySuccessfulModifier(block: AccountBlock) =>
      websocket.onSemanticallySuccessfulModifier(block)
    case ChangedVault(_) =>
      websocket.onChangedVault()
    case NewExecTransactionsEvent(newExecTxs: Iterable[SidechainTypes#SCAT]) =>
      websocket.onNewExecTransactionsEvent(newExecTxs.toSeq.asInstanceOf[Seq[EthereumTransaction]])
    case NotifySyncStart(syncStatus: SyncStatus) =>
      websocket.onSyncStart(syncStatus)
    case NotifySyncStop =>
      websocket.onSyncStop()
    case NotifySyncUpdate(syncStatus: SyncStatus) =>
      websocket.onSyncStart(syncStatus)
  }
}

object WebSocketAccountServerRef {

  var sidechainNodeViewHolderRef: ActorRef = null
  var rpcProcessor: RpcProcessor = null

  def props(sidechainNodeViewHolderRef: ActorRef, rpcProcessor: RpcProcessor, wsPort: Int)
           (implicit ec: ExecutionContext): Props = {
    this.sidechainNodeViewHolderRef = sidechainNodeViewHolderRef
    this.rpcProcessor = rpcProcessor
    Props(new WebSocketAccountServer(wsPort))
  }

  def apply(sidechainNodeViewHolderRef: ActorRef, rpcProcessor: RpcProcessor, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, rpcProcessor, wsPort))

  def apply(name: String, sidechainNodeViewHolderRef: ActorRef, rpcProcessor: RpcProcessor, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, rpcProcessor, wsPort), name)
}

package com.horizen.websocket.server

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedMempool, SemanticallySuccessfulModifier}
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

class WebSocketServer(wsPort: Int)
  extends Actor
  with ScorexLogging {
  val websocket = new WebSocketServerImpl(wsPort, classOf[WebSocketServerEndpoint]);

  try {
    websocket.start()
  } catch {
    case _: Throwable => println("Couldn't start websocket server!")
  }


  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ChangedMempool[_]])
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[_]])
  }

  override def postStop(): Unit = {
    websocket.stop()
    super.postStop()
  }

  override def receive: Receive = {
    checkMessage orElse {
      case message: Any => log.error("WebsocketServer received strange message: " + message)
    }
  }

  protected def checkMessage: Receive = {
    case ChangedMempool(_) => {
      websocket.onMempoolChanged()
    }
    case SemanticallySuccessfulModifier(block: SidechainBlock) => {
      websocket.onSemanticallySuccessfulModifier(block)
    }
  }
}

object WebSocketServerRef {

  var sidechainNodeViewHolderRef: ActorRef = null

  def props(sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit ec: ExecutionContext): Props = {
    this.sidechainNodeViewHolderRef = sidechainNodeViewHolderRef
    Props(new WebSocketServer(wsPort))
  }

  def apply(sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, wsPort))

  def apply(name: String, sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, wsPort), name)
}

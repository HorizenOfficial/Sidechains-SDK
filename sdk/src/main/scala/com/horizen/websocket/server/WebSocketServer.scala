package com.horizen.websocket.server

import java.util

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.SidechainAppEvents
import javax.websocket.Session
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, ChangedMempool, SemanticallySuccessfulModifier}
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

class WebSocketServer  (sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
  extends Actor
  with ScorexLogging {
  var websocket: WebSocketServerImpl = null;

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ChangedMempool[_]])
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[_]])
    wsPort match {
      case 0 =>     this.websocket = new WebSocketServerImpl(8025, classOf[WebSocketServerEndpoint])
      case _ =>     this.websocket = new WebSocketServerImpl(wsPort, classOf[WebSocketServerEndpoint])
    }
    this.websocket.start()
  }

  override def receive: Receive = {
    checkMessage orElse {
      case message: Any => log.error("WebsocketServer received strange message: " + message)
    }
  }

  protected def checkMessage: Receive = {
    case ChangedMempool(_)  => {
      val sessionToRemove: util.ArrayList[Session] = new util.ArrayList[Session]()
      WebSocketServerEndpoint.sessions.forEach(session =>{
        if (session.isOpen) {
          new SidechainNodeChannelImpl(session).sendRawMempool(-1,2)
        }
        else {
          sessionToRemove.add(session)
        }
      })
      removeOutdatedSession(sessionToRemove)
    }
    case SemanticallySuccessfulModifier(_) => {
      val sessionToRemove: util.ArrayList[Session] = new util.ArrayList[Session]()
      WebSocketServerEndpoint.sessions.forEach(session =>{
        if (session.isOpen) {
          new SidechainNodeChannelImpl(session).sendBestBlock()
        }
        else {
          sessionToRemove.add(session)
        }
      })
      removeOutdatedSession(sessionToRemove)
    }
  }


  private def removeOutdatedSession(toRemove: util.ArrayList[Session]): Unit = {
    toRemove.forEach(s => {
      WebSocketServerEndpoint.sessions.remove(s)
    })
  }
}

object WebSocketServerRef {

  var sidechainNodeViewHolderRef: ActorRef = null

  def props(sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit ec: ExecutionContext) : Props = {
    this.sidechainNodeViewHolderRef = sidechainNodeViewHolderRef
    Props(new WebSocketServer(sidechainNodeViewHolderRef, wsPort))
  }

  def apply(sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, wsPort))

  def apply(name: String, sidechainNodeViewHolderRef: ActorRef, wsPort: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, wsPort), name)
}

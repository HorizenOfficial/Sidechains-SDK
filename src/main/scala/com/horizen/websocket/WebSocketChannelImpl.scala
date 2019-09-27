package com.horizen.websocket

import java.net.{InetSocketAddress, URI}

import javax.websocket.{ClientEndpoint, ClientEndpointConfig, CloseReason, ContainerProvider, Endpoint, EndpointConfig, MessageHandler, OnClose, OnMessage, OnOpen, SendHandler, SendResult, Session, WebSocketContainer}
import org.glassfish.tyrus.client.ClientManager
import scorex.util.ScorexLogging

import scala.util.Try

@ClientEndpoint
class WebSocketChannelImpl(configuration : WebSocketChannelConfiguration) extends WebSocketChannel with ScorexLogging {

  private var webSocketHandler : WebSocketHandler = _
  private var userSession : Session = null

  override def isOpened: Boolean =
    userSession != null && userSession.isOpen

  var host = configuration.schema +"://" + configuration.remoteAddress.getHostName + ":" + configuration.remoteAddress.getPort

  override def open(): Try[Unit] = Try {
    //if(userSession==null || !userSession.isOpen){
    var host = configuration.schema +"://" + configuration.remoteAddress.getHostName + ":" + configuration.remoteAddress.getPort
    var container = ContainerProvider.getWebSocketContainer()
    userSession = container.connectToServer(this, new URI(host))
    userSession.addMessageHandler(new MessageHandler.Whole[String]() {
      override def onMessage(t: String): Unit = {
        println("Message received... " + t)
        //webSocketHandler.onReceivedMessage(t)

      }
    })
/*      userSession = ClientManager.createClient().connectToServer(new Endpoint {

        override def onOpen(session: Session, endpointConfig: EndpointConfig): Unit = {
          println("Session opened")

          session.addMessageHandler(new MessageHandler.Whole[String]() {
            override def onMessage(t: String): Unit = {
              println("Message received... " + t)
              //webSocketHandler.onReceivedMessage(t)

            }
          })


          userSession = session
          userSession.getBasicRemote.sendText("hello")
        }
      }, ClientEndpointConfig.Builder.create().build(), URI.create(host))*/

     // userSession = ClientManager.createClient().connectToServer(this, new URI(host))
      /*userSession.addMessageHandler(new MessageHandler.Whole[String]() {
        override def onMessage(t: String): Unit = {
          println("Message received... " + t)
          //webSocketHandler.onReceivedMessage(t)

        }
      })*/
      //userSession.getBasicRemote.sendText("hello")

    //this.userSession = ClientManager.createClient().connectToServer(classOf[WebSocketChannelImpl], new URI(host))

    //}else throw new RuntimeException("Connection already opened")
  }


/*  @OnMessage
  def onMessage(message : String) = {
    println(message.toUpperCase())
  }*/

  override def close(): Try[Unit] = Try {
    userSession.close()
  }

  override def sendMessage(message: String): Unit = {
    userSession.getAsyncRemote().sendText(message
      /*, new SendHandler {
      override def onResult(sendResult: SendResult): Unit = {
        if(!sendResult.isOK)
          webSocketHandler.onSendMessageErrorOccurred(message, sendResult.getException)
        else println("Message sent")
      }
    }*/
    )

  }

  override def setWebSocketHandler(handler: WebSocketHandler): Unit =
    webSocketHandler = handler

}

object  WebSocketClientApp extends App {
  var conf = new WebSocketChannelConfiguration(remoteAddress = new InetSocketAddress("echo.websocket.org", 80))
  //var conf = new WebSocketChannelConfiguration(remoteAddress = new InetSocketAddress("localhost", 8080))


  var client = new WebSocketChannelImpl(conf)
  println(client.isOpened)
  println(client.open())
  println(client.isOpened)
  client.sendMessage("helloooooo")
}

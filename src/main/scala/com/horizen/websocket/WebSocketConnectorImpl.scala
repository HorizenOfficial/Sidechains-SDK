package com.horizen.websocket

import java.net.URI

import javax.websocket.{CloseReason, MessageHandler, Session}
import org.glassfish.tyrus.client.{ClientManager, ClientProperties}
import scorex.util.ScorexLogging

import scala.concurrent.{Future, Promise}
import scala.util.Try

class WebSocketConnectorImpl(conf: WebSocketConnectorConfiguration, msgHandler: WebSocketMessageHandler, reconHandler: WebSocketReconnectionHandler) extends WebSocketConnector[WebSocketChannelImpl] with ScorexLogging {

  private var configuration : WebSocketConnectorConfiguration = conf
  private var messageHandler : WebSocketMessageHandler = msgHandler
  private var userSession : Session = _
  private var reconnectionHandler : WebSocketReconnectionHandler = reconHandler
  private var channel : WebSocketChannelImpl = _
  private val client = ClientManager.createClient()
  private val reconnectHandler: ClientManager.ReconnectHandler = new ClientManager.ReconnectHandler() {
    /**
      * Delay before next connection attempt.
      */
    override def getDelay: Long = {
      if(reconnectionHandler != null)
        reconnectionHandler.getDelay
      else configuration.reconnectionDelay.toSeconds
    }

    // will be executed whenever @OnClose annotated method (or Endpoint.onClose(..)) is executed on client side.
    // this should happen when established connection is lost for any reason
    override def onDisconnect(closeReason: CloseReason): Boolean = {
      log.info("onDisconnect. Reason: " + closeReason.toString)
      if (reconnectionHandler != null) {
        if (closeReason.getCloseCode.getCode == 1000)
          reconnectionHandler.onDisconnection(DisconnectionCode.ON_SUCCESS, closeReason.getReasonPhrase)
        else
          reconnectionHandler.onDisconnection(DisconnectionCode.UNEXPECTED, closeReason.getReasonPhrase)
      }
      else true
    }

    // is invoked when client fails to connect to remote endpoint
    override def onConnectFailure(exception: Exception): Boolean = {
      if (reconnectionHandler != null)
        reconnectionHandler.onConnectionFailed(exception)
      else true
    }
  }

  override def isStarted : Boolean =
    userSession != null && userSession.isOpen

  override def start(): Try[WebSocketChannelImpl] = Try {

    if (isStarted) throw new IllegalStateException("Connector is already started.")
    else if(configuration == null) throw new IllegalStateException("Configuration must be not null.")
    else {
      client.getProperties.put(ClientProperties.RECONNECT_HANDLER, reconnectHandler)
      client.getProperties.put(ClientProperties.HANDSHAKE_TIMEOUT, String.valueOf(configuration.connectionTimeout.toMillis))
      log.info("Starting web socket connector...")
      userSession = client.connectToServer(classOf[WebSocketChannelImpl], new URI(configuration.bindAddress))
      log.info("Web socket connector started.")
      if(channel == null)
        channel = new WebSocketChannelImpl()

      if(messageHandler != null){
        channel.setWebSocketMessageHandler(messageHandler)
        userSession.addMessageHandler(new MessageHandler.Whole[String]() {
          override def onMessage(t: String): Unit = {
            log.info("Message received from server: " + t)
            messageHandler.onReceivedMessage(t)
          }
        })
      }

      channel.setSession(userSession)
      channel
    }
  }

  override def asyncStart(): Future[Try[WebSocketChannelImpl]] = {
    val promise : Promise[Try[WebSocketChannelImpl]] = Promise[Try[WebSocketChannelImpl]]

    new Thread(new Runnable {
      override def run(): Unit = {
        promise.success(start())
      }
    }).start()

    promise.future
  }

  override def stop(): Try[Unit] = Try {
    log.info("Stopping web socket connector...")
    userSession.close()
    log.info("Web socket connector stopped.")
  }

}

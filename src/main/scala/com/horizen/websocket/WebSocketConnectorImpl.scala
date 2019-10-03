package com.horizen.websocket

import java.net.URI
import java.util.concurrent.locks.ReentrantLock

import javax.websocket.{CloseReason, MessageHandler, Session}
import org.glassfish.tyrus.client.{ClientManager, ClientProperties}
import scorex.util.ScorexLogging

import scala.concurrent.{Future, Promise}
import scala.util.Try

class WebSocketConnectorImpl extends WebSocketConnector[WebSocketChannelImpl] with ScorexLogging {

  private var configuration : WebSocketConnectorConfiguration = _
  private var messageHandler : WebSocketMessageHandler = _
  private var userSession : Session = _
  private var reconnectionHandler : WebSocketReconnectionHandler = _
  private var channel : WebSocketChannelImpl = _
  private val lock = new ReentrantLock()
  private val reconnectionHandlerCondition = lock.newCondition()
  private var reconnectionHandlerCanBeNull : Boolean = false

  override def isStarted : Boolean =
    userSession != null && userSession.isOpen

  override def start(): Try[WebSocketChannelImpl] = Try {
    reconnectionHandlerCanBeNull = false

    if (userSession == null || !userSession.isOpen) {

      if(configuration != null){

        var host = configuration.schema + "://" + configuration.remoteAddress.getHostName + ":" + configuration.remoteAddress.getPort

        val client = ClientManager.createClient()
        val reconnectHandler: ClientManager.ReconnectHandler = new ClientManager.ReconnectHandler() {

          var counter = 0

          /**
            * Delay before next connection attempt. Default value is 5 seconds
            */
          override def getDelay: Long = configuration.reconnectionDelay

          // will be executed whenever @OnClose annotated method (or Endpoint.onClose(..)) is executed on client side.
          // this should happen when established connection is lost for any reason
          override def onDisconnect(closeReason: CloseReason): Boolean = {
            counter = counter + 1
            if (counter <= configuration.reconnectionMaxAttempts) {
              log.info("onDisconnect. Reason: " + closeReason.toString + " Reconnecting... (attempt " + counter + ")")
              lock.lock()
              try {
                if (reconnectionHandler != null) {
                  var doNextAttempt : Boolean = true

                  if (closeReason.getCloseCode.getCode == 1000)
                    doNextAttempt = reconnectionHandler.onDisconnection(DisconnectionCode.ON_SUCCESS, closeReason.getReasonPhrase)
                  else
                      /**
                        * When 'stop' method is called in a 'normal' way (when the server is still started), then it's called 'onDisconnect(DisconnectionCode.ON_SUCCESS)'.
                        * If, instead, the server stops for any reason, then it's called onDisconnect(DisconnectionCode.UNEXPECTED)'.
                        * If we call 'stop' when the server is not started, then the 'reconnectionHandlerCanBeNull' will not be set to 'true'.
                        * Therefore, 'stop' method will never complete.
                        * So, we notify it properly.
                        */
                      doNextAttempt = reconnectionHandler.onDisconnection(DisconnectionCode.UNEXPECTED, closeReason.getReasonPhrase)

                  reconnectionHandlerCanBeNull = true
                  reconnectionHandlerCondition.signal()
                  doNextAttempt
                }
                else true
              }finally {
                lock.unlock()
              }
            } else false
          }

          // is invoked when client fails to connect to remote endpoint
          override def onConnectFailure(exception: Exception): Boolean = {
            counter = counter + 1
            if (counter <= configuration.reconnectionMaxAttempts) {
              log.info("onConnectFailure. Reconnecting... (attempt " + counter + ") " + exception.getMessage)
                if (reconnectionHandler != null)
                  reconnectionHandler.onConnectionFailed(exception)
                else true
            } else false
          }
        }

        client.getProperties.put(ClientProperties.RECONNECT_HANDLER, reconnectHandler)
        client.getProperties.put(ClientProperties.HANDSHAKE_TIMEOUT, String.valueOf(configuration.connectionTimeout))

        try {
          log.info("Starting web socket connector...")

          userSession = client.connectToServer(classOf[WebSocketChannelImpl], new URI(host))

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

        } catch {
          case e: Throwable => throw e
        }
      }else throw new IllegalStateException("Configuration must be not null.")

    } else throw new IllegalStateException("Connector is already started.")
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
    configuration = null

    /**
      * During the lifecycle of the connector, we provide to it all needed information once.
      * Therefore, when we close the connection we set to null all handlers, in order to correctly start again the connector.
      * With this logic, when we set to null the reconnection handler, it can not have anymore the possibility to run its 'onDisconnect' method.
      * That method must be called before to set to null the reference to the object.
      * For do this we need a synchronization between two methods:'onDisconnect' and 'stop'.
      */
    lock.lock()
    try {
      while(!reconnectionHandlerCanBeNull)
        reconnectionHandlerCondition.await()

      reconnectionHandler = null
    } finally {
      lock.unlock()
    }
    messageHandler = null
    log.info("Web socket connector stopped.")
  }

  override def setMessageHandler(handler: WebSocketMessageHandler): Boolean = {
    if(messageHandler == null) {
      messageHandler = handler
      true
    }else false
  }

  override def setConfiguration(configuration: WebSocketConnectorConfiguration) : Boolean = {
    if(this.configuration == null) {
      this.configuration = configuration
      true
    }else false
  }

  override def setReconnectionHandler(handler: WebSocketReconnectionHandler): Boolean = {
    if(reconnectionHandler == null) {
      this.reconnectionHandler = handler
      true
    }else false
  }

}

package io.horizen.websocket.server

import io.horizen.websocket.client.WebSocketConnector
import jakarta.websocket._
import org.glassfish.tyrus.server.Server
import sparkz.util.SparkzLogging

import scala.concurrent.{Future, Promise}
import scala.util.Try

@ClientEndpoint
class WebSocketServerBaseImpl(bindPort: Int, configuration: Class[_]) extends WebSocketConnector with SparkzLogging {

  var server: Server = null

  override def isStarted: Boolean =
    server != null

  override def start(): Try[Unit] = Try {

    if (isStarted) throw new IllegalStateException("Connector is already started.")
    this.server = new Server("localhost", bindPort, null, null, configuration)
    this.server.start()
  }

  override def asyncStart(): Future[Try[Unit]] = {
    val promise: Promise[Try[Unit]] = Promise[Try[Unit]]

    new Thread(new Runnable {
      override def run(): Unit = {
        promise.success(start())
      }
    }).start()

    promise.future
  }

  override def stop(): Try[Unit] = Try {
    log.info("Stopping web socket server...")
    if (this.server != null) {
      this.server.stop()
      this.server = null
      log.info("Web socket server stopped.")
    }
  }

}
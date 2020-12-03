package com.horizen.websocket.server

import scala.concurrent.Future
import scala.util.{Try}

trait WebSocketServerConnector {
  def isStarted(): Boolean

  def start(): Try[Unit]

  def asyncStart(): Future[Try[Unit]]

  def stop(): Try[Unit]
}

package io.horizen.account.websocket

import jakarta.websocket.HandshakeResponse
import jakarta.websocket.server.{HandshakeRequest, ServerEndpointConfig}
import org.glassfish.tyrus.core.RequestContext
import sparkz.util.SparkzLogging


class WebSocketAccountConfigurator() extends ServerEndpointConfig.Configurator with SparkzLogging {

  override def modifyHandshake(config: ServerEndpointConfig, request: HandshakeRequest, response: HandshakeResponse): Unit = {
    val remoteAddress = request.asInstanceOf[RequestContext].getRemoteAddr
    if (!WebSocketAccountConfigurator.allowedIPs.contains(remoteAddress)) {
      throw new SecurityException(s"IP ${remoteAddress} is not allowed to connect to the WebSocket")
    }
  }

}

object WebSocketAccountConfigurator {
  var allowedIPs = Seq("127.0.0.1")
  def apply(allowedIPs: Seq[String]): Unit = {
    this.allowedIPs = allowedIPs
  }
}

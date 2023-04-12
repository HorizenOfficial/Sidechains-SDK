package io.horizen.account.websocket

import jakarta.websocket.HandshakeResponse
import jakarta.websocket.server.{HandshakeRequest, ServerEndpointConfig}
import org.glassfish.tyrus.core.RequestContext
import sparkz.util.SparkzLogging

import java.net.InetAddress

class WebSocketAccountConfigurator() extends ServerEndpointConfig.Configurator with SparkzLogging {

  override def modifyHandshake(config: ServerEndpointConfig, request: HandshakeRequest, response: HandshakeResponse): Unit = {
    val remoteAddress = InetAddress.getByName(request.asInstanceOf[RequestContext].getRemoteAddr)
    if ((WebSocketAccountConfigurator.allowedIPs.isEmpty && !remoteAddress.isAnyLocalAddress && !remoteAddress.isLoopbackAddress && !remoteAddress.isSiteLocalAddress) ||
      (WebSocketAccountConfigurator.allowedIPs.nonEmpty && !WebSocketAccountConfigurator.allowedIPs.contains(remoteAddress.getHostAddress)))
      throw new SecurityException(s"IP ${remoteAddress} is not allowed to connect to the WebSocket")
  }

}

object WebSocketAccountConfigurator {
  var allowedIPs: Seq[String] = Seq()
  def apply(allowedIPs: Seq[String]): Unit = {
    this.allowedIPs = allowedIPs
  }
}

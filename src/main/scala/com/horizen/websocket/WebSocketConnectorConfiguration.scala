package com.horizen.websocket

import java.net.InetSocketAddress

case class WebSocketConnectorConfiguration(
                                          schema : String = "ws",
                                          remoteAddress: InetSocketAddress,
                                          connectionTimeout : Int,
                                          reconnectionMaxAttempts : Int) {

}

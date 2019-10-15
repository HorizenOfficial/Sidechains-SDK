package com.horizen.websocket

import scala.concurrent.duration.FiniteDuration

case class WebSocketConnectorConfiguration(
                                            bindAddress: String,
                                            connectionTimeout: FiniteDuration,
                                            reconnectionDelay: FiniteDuration,
                                            reconnectionMaxAttempts: Int) {

}

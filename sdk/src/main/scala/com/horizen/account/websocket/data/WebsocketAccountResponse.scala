package com.horizen.account.websocket.data

import com.fasterxml.jackson.annotation.{JsonProperty, JsonView}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.horizen.serialization.Views

@JsonView (Array (classOf[Views.Default] ) )
class WebsocketAccountResponse(@JsonProperty("method") val method: String,
                               @JsonProperty("params") val params: ObjectNode) {
  @JsonProperty("jsonrpc") protected val jsonrpc = "2.0"
}

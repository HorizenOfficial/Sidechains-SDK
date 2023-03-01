package com.horizen.account.websocket.data

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import com.horizen.account.api.rpc.response.RpcResponseSuccess

import java.math.BigInteger

@JsonIgnoreProperties(Array("id", "result"))
class WebSocketAccountEvent(@JsonProperty("method")
                            val method: String,
                            @JsonProperty("params")
                            val params: Object) extends RpcResponseSuccess(null, params){
}

class WebSocketAccountEventParams(@JsonProperty("subscription")
                                  val subscription: BigInteger,
                                  @JsonProperty("result")
                                  val result: Object)

class WebSocketAccountEventLogParams(@JsonProperty("removed") val removed: Boolean, subscription: BigInteger, result: Object) extends WebSocketAccountEventParams(subscription, result)
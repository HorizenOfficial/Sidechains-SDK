package io.horizen.account.websocket.data

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonInclude, JsonProperty}
import io.horizen.account.api.rpc.response.RpcResponseSuccess

import java.math.BigInteger

@JsonIgnoreProperties(Array("id", "result"))
class WebSocketAccountEvent(@JsonProperty("method")
                            val method: String = "eth_subscription",
                            @JsonProperty("params")
                            val params: Object) extends RpcResponseSuccess(null, params){
}

class WebSocketAccountEventParams(@JsonProperty("subscription")
                                  val subscription: BigInteger,
                                  @JsonProperty("result")
                                  val result: Object)

class WebSocketSyncEvent(@JsonProperty("syncing")
                         val syncing: Boolean = true,
                         @JsonProperty("status")
                         @JsonInclude(Include.NON_NULL)
                         val status: WebSocketSyncStatus)
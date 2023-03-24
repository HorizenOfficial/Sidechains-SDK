package io.horizen.account.websocket.data

import com.fasterxml.jackson.annotation.JsonProperty
import io.horizen.network.SyncStatus

class WebSocketSyncStatus(syncStatus: SyncStatus) {
  @JsonProperty("startingBlock")
  val startingBlock: Long = syncStatus.startingBlock.longValue()
  @JsonProperty("currentBlock")
  val currentBlock: Long = syncStatus.currentBlock.longValue()
  @JsonProperty("highestBlock")
  val highestBlock: Long = syncStatus.highestBlock.longValue()
}

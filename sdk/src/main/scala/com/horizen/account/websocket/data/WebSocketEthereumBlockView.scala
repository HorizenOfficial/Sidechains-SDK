package com.horizen.account.websocket.data

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import com.horizen.account.api.rpc.types.EthereumBlockView
import io.horizen.evm.{Address, Hash}

import java.math.BigInteger

@JsonIgnoreProperties(Array("size"))
class WebSocketEthereumBlockView(ethereumBlockView: EthereumBlockView) {
  @JsonProperty("difficulty")
  val difficulty: String = ethereumBlockView.difficulty
  @JsonProperty("extraData")
  val extraData: String = ethereumBlockView.extraData
  @JsonProperty("gasLimit")
  val gasLimit: BigInteger = ethereumBlockView.gasLimit
  @JsonProperty("gasUsed")
  val gasUsed: BigInteger = ethereumBlockView.gasUsed
  @JsonProperty("logsBloom")
  val logsBloom: Array[Byte] = ethereumBlockView.logsBloom
  @JsonProperty("miner")
  val miner: Address = ethereumBlockView.miner
  @JsonProperty("nonce")
  val nonce: String = ethereumBlockView.nonce
  @JsonProperty("number")
  val number: BigInteger = ethereumBlockView.number
  @JsonProperty("parentHash")
  val parentHash: Hash = ethereumBlockView.parentHash
  @JsonProperty("receiptsRoot")
  val receiptsRoot: Hash = ethereumBlockView.receiptsRoot
  @JsonProperty("sha3Uncles")
  val sha3Uncles: String = ethereumBlockView.sha3Uncles
  @JsonProperty("stateRoot")
  val stateRoot: Hash = ethereumBlockView.stateRoot
  @JsonProperty("timestamp")
  val timestamp: BigInteger = ethereumBlockView.timestamp
  @JsonProperty("transactionsRoot")
  val transactionsRoot: Hash = ethereumBlockView.transactionsRoot
  @JsonProperty("hash")
  val hash: Hash = ethereumBlockView.hash
}

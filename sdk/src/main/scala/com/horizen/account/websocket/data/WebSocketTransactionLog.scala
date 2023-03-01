package com.horizen.account.websocket.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.horizen.account.receipt.{EthereumConsensusDataLog, EthereumReceipt}
import io.horizen.evm.{Address, Hash}

import java.math.BigInteger

class WebSocketTransactionLog(ethereumReceipt: EthereumReceipt,
                              log: EthereumConsensusDataLog,
                              index: Int) {
  @JsonProperty("transactionHash")
  val transactionHash: Array[Byte] = ethereumReceipt.transactionHash
  @JsonProperty("transactionIndex")
  val transactionIndex: BigInteger = new BigInteger(String.valueOf(ethereumReceipt.transactionIndex))
  @JsonProperty("blockHash")
  val blockHash: Array[Byte] = ethereumReceipt.blockHash
  @JsonProperty("blockNumber")
  val blockNumber: BigInteger = new BigInteger(String.valueOf(ethereumReceipt.blockNumber))
  @JsonProperty("address")
  val address: Address = log.address
  @JsonProperty("data")
  val data: Array[Byte] = log.data
  @JsonProperty("topics")
  val topics: Array[Hash] = log.topics
  @JsonProperty("logIndex")
  val logIndex: BigInteger = new BigInteger(String.valueOf(index))
}

package com.horizen.account.receipt

import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import java.util

case class EthereumLog(
                         consensusDataLog : EthereumConsensusDataLog,
                         transactionHash: Array[Byte],
                         transactionIndex: Int,
                         blockHash: Array[Byte],
                         blockNumber: Int,
                         logIndex : Int,
                         removed : Int
                       ) extends BytesSerializable  {


  override type M = EthereumLog

  override def serializer: ScorexSerializer[EthereumLog] = EthereumLogSerializer

  def this(consensusDataLog : EthereumConsensusDataLog) {
    this(consensusDataLog, new Array[Byte](0), -1, new Array[Byte](0), -1, -1, -1)
  }

  def update(txHash: Array[Byte], txIndex: Int, blHash: Array[Byte], blNumber: Int, idx: Int): EthereumLog =
    EthereumLog(this.consensusDataLog, txHash, txIndex, blHash, blockNumber, idx, removed=0)

  override def toString: String = {

    var txHashStr : String = "null"
    var blockHashStr: String = "null"

    if (transactionHash != null)
      txHashStr = BytesUtils.toHexString(transactionHash)

    if (blockHash != null)
      blockHashStr = BytesUtils.toHexString(blockHash)

    val infoNonConsensusStr : String =
      String.format(s" - (non consensus data) {txHash=$txHashStr, txIndex=$transactionIndex, blockHash=$blockHashStr, blockNumber=$blockNumber, logIndex=$logIndex, removed=$removed}")

    consensusDataLog.toString.concat(infoNonConsensusStr)
  }
}

object EthereumLogSerializer extends ScorexSerializer[EthereumLog]{

  override def serialize(log: EthereumLog, writer: Writer): Unit = {
    // consensus data
    writer.putBytes(log.consensusDataLog.address.toBytes)

    // array of elements of fixed data size (32 bytes)
    val topicsArraySize = log.consensusDataLog.topics.length
    writer.putInt(topicsArraySize)
    for (i <- 0 until topicsArraySize) {
      writer.putBytes(log.consensusDataLog.topics(i).toBytes)
    }

    val data = log.consensusDataLog.data
    writer.putInt(data.length)
    writer.putBytes(data)

    // derived
    // TODO (shall we put these 4? They are the same as belonging receipt
    writer.putBytes(log.transactionHash)
    writer.putInt(log.transactionIndex)
    writer.putBytes(log.blockHash)
    writer.putInt(log.blockNumber)

    writer.putInt(log.logIndex)
    writer.putInt(log.removed)
  }

  override def parse(reader: Reader): EthereumLog = {
    // consensus data
    val address: Array[Byte] = reader.getBytes(20)

    val topicsArraySize: Int = reader.getInt
    val topics: util.ArrayList[Hash] = new util.ArrayList[Hash]
    for (_ <- 0 until topicsArraySize) {
      topics.add(Hash.FromBytes(reader.getBytes(Hash.LENGTH)))
    }

    val dataLength: Int = reader.getInt
    val data: Array[Byte] = reader.getBytes(dataLength)

    val log: EthereumConsensusDataLog = EthereumConsensusDataLog(
      Address.FromBytes(address), topics.toArray(new Array[Hash](0)), data)

    // derived
    // TODO (shall we put these 4? They are the same as belonging receipt and could be derived
    val txHash: Array[Byte] = reader.getBytes(32)
    val txIndex: Int = reader.getInt
    val blockHash: Array[Byte] = reader.getBytes(32)
    val blockNumber: Int = reader.getInt

    val logIndex: Int = reader.getInt
    val removed: Int = reader.getInt

    EthereumLog(log, txHash, txIndex, blockHash, blockNumber, logIndex, removed)
  }
}

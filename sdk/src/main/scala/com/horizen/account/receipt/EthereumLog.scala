package com.horizen.account.receipt

import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}

import java.util

case class EthereumLog(
                         consensusDataLog : EvmLog,
                         transactionHash: Array[Byte],
                         transactionIndex: Int,
                         blockHash: Array[Byte],
                         blockNumber: Int,
                         logIndex : Int
                       ) {
  override def toString: String = {

    var txHashStr : String = "null"
    var blockHashStr: String = "null"

    if (transactionHash != null)
      txHashStr = BytesUtils.toHexString(transactionHash)

    if (blockHash != null)
      blockHashStr = BytesUtils.toHexString(blockHash)

    val infoNonConsensusStr : String =
      String.format(s" - (log non consensus data) {txHash=$txHashStr, txIndex=$transactionIndex, blockHash=$blockHashStr, blockNumber=$blockNumber, logIndex=$logIndex}")

    consensusDataLog.toString.concat(infoNonConsensusStr)
  }
}

object EthereumLog {
  def derive(consensusDataLog : EvmLog, txHash: Array[Byte], txIndex: Int, blHash: Array[Byte], blNumber: Int, idx: Int): EthereumLog =
    EthereumLog(consensusDataLog, txHash, txIndex, blHash, blNumber, idx)
}

object EvmLogSerializer extends ScorexSerializer[EvmLog]{

  override def serialize(log: EvmLog, writer: Writer): Unit = {
    writer.putBytes(log.address.toBytes)

    // array of elements of fixed data size (32 bytes)
    val topicsArraySize = log.topics.length
    writer.putInt(topicsArraySize)
    for (i <- 0 until topicsArraySize) {
      writer.putBytes(log.topics(i).toBytes)
    }

    val data = log.data
    writer.putInt(data.length)
    writer.putBytes(data)
  }

  override def parse(reader: Reader): EvmLog = {
    val address: Array[Byte] = reader.getBytes(Address.LENGTH)

    val topicsArraySize: Int = reader.getInt
    val topics: util.ArrayList[Hash] = new util.ArrayList[Hash]
    for (_ <- 0 until topicsArraySize) {
      topics.add(Hash.FromBytes(reader.getBytes(Hash.LENGTH)))
    }

    val dataLength: Int = reader.getInt
    val data: Array[Byte] = reader.getBytes(dataLength)

    new EvmLog(Address.FromBytes(address), topics.toArray(new Array[Hash](0)), data)
  }
}

package com.horizen.account.receipt

import io.horizen.evm.{Address, Hash}
import org.web3j.rlp._
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}

import java.util


object EvmLogUtils extends SparkzSerializer[EthereumConsensusDataLog] {
  
  def rlpEncode(r: EthereumConsensusDataLog): Array[Byte] = {
    val values = asRlpValues(r)
    val rlpList = new RlpList(values)
    val encoded = RlpEncoder.encode(rlpList)
    encoded
  }

  def rlpDecode(rlpData: Array[Byte]): EthereumConsensusDataLog = {
    val rlpList = RlpDecoder.decode(rlpData).getValues.get(0).asInstanceOf[RlpList]
    rlpDecode(rlpList)
  }

  def rlpDecode(values: RlpList): EthereumConsensusDataLog = {
    val addressBytes = values.getValues.get(0).asInstanceOf[RlpString].getBytes
    val address = new Address(addressBytes)
    val topicsRlp = values.getValues.get(1).asInstanceOf[RlpList]
    val hashList = new util.ArrayList[Hash]
    val topicsListSize = topicsRlp.getValues.size
    if (topicsListSize > 0) { // loop on list and decode all topics
      for (i <- 0 until topicsListSize) {
        val topicBytes = topicsRlp.getValues.get(i).asInstanceOf[RlpString].getBytes
        hashList.add(new Hash(topicBytes))
      }
    }
    val topics = hashList.toArray(new Array[Hash](0))
    val data = values.getValues.get(2).asInstanceOf[RlpString].getBytes

    EthereumConsensusDataLog(address, topics, data)
  }

  def asRlpValues(log: EthereumConsensusDataLog): util.List[RlpType] = {
    val result = new util.ArrayList[RlpType]
    val rlpTopics = new util.ArrayList[RlpType]
    result.add(RlpString.create(log.address.toBytes))
    for (t <- log.topics) {
      rlpTopics.add(RlpString.create(t.toBytes))
    }
    result.add(new RlpList(rlpTopics))
    if (log.data == null){
      result.add(RlpString.create(Array[Byte](0)))
    }
    else {
      result.add(RlpString.create(log.data))
    }
    result
  }

  override def serialize(log: EthereumConsensusDataLog, writer: Writer): Unit = {
    writer.putBytes(log.address.toBytes)

    // array of elements of fixed data size (32 bytes)
    val topicsArraySize = log.topics.length
    writer.putInt(topicsArraySize)
    for (i <- 0 until topicsArraySize) {
      writer.putBytes(log.topics(i).toBytes)
    }

    val data = if (log.data != null) log.data else Array[Byte](0)
    writer.putInt(data.length)
    writer.putBytes(data)
  }

  override def parse(reader: Reader): EthereumConsensusDataLog = {
    val address: Address = new Address(reader.getBytes(Address.LENGTH))

    val topicsArraySize: Int = reader.getInt
    val topics: util.ArrayList[Hash] = new util.ArrayList[Hash]
    for (_ <- 0 until topicsArraySize) {
      topics.add(new Hash(reader.getBytes(Hash.LENGTH)))
    }

    val dataLength: Int = reader.getInt
    val data: Array[Byte] = reader.getBytes(dataLength)

    EthereumConsensusDataLog(address, topics.toArray(new Array[Hash](0)), data)
  }
}

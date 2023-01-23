package com.horizen.account.receipt


import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import org.web3j.rlp.{RlpDecoder, RlpEncoder, RlpList, RlpString, RlpType}
import sparkz.core.serialization.SparkzSerializer
import scorex.util.serialization.{Reader, Writer}

import java.util


object EvmLogUtils extends SparkzSerializer[EvmLog] {
  
  def rlpEncode(r: EvmLog): Array[Byte] = {
    val values = asRlpValues(r)
    val rlpList = new RlpList(values)
    val encoded = RlpEncoder.encode(rlpList)
    encoded
  }

  def rlpDecode(rlpData: Array[Byte]): EvmLog = {
    val rlpList = RlpDecoder.decode(rlpData).getValues.get(0).asInstanceOf[RlpList]
    rlpDecode(rlpList)
  }

  def rlpDecode(values: RlpList): EvmLog = {
    val addressBytes = values.getValues.get(0).asInstanceOf[RlpString].getBytes
    val address = Address.fromBytes(addressBytes)
    val topicsRlp = values.getValues.get(1).asInstanceOf[RlpList]
    val hashList = new util.ArrayList[Hash]
    val topicsListSize = topicsRlp.getValues.size
    if (topicsListSize > 0) { // loop on list and decode all topics
      for (i <- 0 until topicsListSize) {
        val topicBytes = topicsRlp.getValues.get(i).asInstanceOf[RlpString].getBytes
        hashList.add(Hash.fromBytes(topicBytes))
      }
    }
    val topics = hashList.toArray(new Array[Hash](0))
    val data = values.getValues.get(2).asInstanceOf[RlpString].getBytes

    new EvmLog(address, topics, data)
  }

  def asRlpValues(log: EvmLog): util.List[RlpType] = {
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

  override def serialize(log: EvmLog, writer: Writer): Unit = {
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

  override def parse(reader: Reader): EvmLog = {
    val address: Array[Byte] = reader.getBytes(Address.LENGTH)

    val topicsArraySize: Int = reader.getInt
    val topics: util.ArrayList[Hash] = new util.ArrayList[Hash]
    for (_ <- 0 until topicsArraySize) {
      topics.add(Hash.fromBytes(reader.getBytes(Hash.LENGTH)))
    }

    val dataLength: Int = reader.getInt
    val data: Array[Byte] = reader.getBytes(dataLength)

    new EvmLog(Address.fromBytes(address), topics.toArray(new Array[Hash](0)), data)
  }
}



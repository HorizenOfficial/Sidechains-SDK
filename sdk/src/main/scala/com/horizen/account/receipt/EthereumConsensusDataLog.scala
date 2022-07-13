package com.horizen.account.receipt


import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import org.web3j.rlp.{RlpDecoder, RlpEncoder, RlpList, RlpString, RlpType}

import java.util


object EthereumConsensusDataLog {
  
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
    val address = Address.FromBytes(addressBytes)
    val topicsRlp = values.getValues.get(1).asInstanceOf[RlpList]
    val hashList = new util.ArrayList[Hash]
    val topicsListSize = topicsRlp.getValues.size
    if (topicsListSize > 0) { // loop on list and decode all topics
      for (i <- 0 until topicsListSize) {
        val topicBytes = topicsRlp.getValues.get(i).asInstanceOf[RlpString].getBytes
        hashList.add(Hash.FromBytes(topicBytes))
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
    result.add(RlpString.create(log.data))
    result
  }
}



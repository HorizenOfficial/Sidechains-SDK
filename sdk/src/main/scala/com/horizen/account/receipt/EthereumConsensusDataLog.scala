package com.horizen.account.receipt

import com.horizen.evm.{Address, Hash}
import com.horizen.utils.BytesUtils

import java.util

case class EthereumConsensusDataLog(
    address: Address,
    topics: Array[Hash],
    data: Array[Byte]
) {
  override def hashCode(): Int = {
    var result = address.hashCode()
    for (topic <- topics) result = 31 * result + topic.hashCode()
    result = 31 * result + util.Arrays.hashCode(data)
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case log: EthereumConsensusDataLog =>
        address.equals(log.address) && topics.sameElements(log.topics) && util.Arrays.equals(data, log.data)
      case _ => false
    }
  }

  override def toString: String =
    "EthereumConsensusDataLog {address=%s, topics=%s, data=%s".format(
      address,
      topics.mkString("Array(", ", ", ")"),
      BytesUtils.toHexString(data)
    )
}

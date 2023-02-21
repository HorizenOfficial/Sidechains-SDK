package com.horizen.account.receipt

import com.horizen.evm.results.EvmLog
import com.horizen.evm.{Address, Hash}

case class EthereumConsensusDataLog(
    address: Address,
    topics: Array[Hash],
    data: Array[Byte]
)

object EthereumConsensusDataLog {
  def apply(log: EvmLog): EthereumConsensusDataLog = EthereumConsensusDataLog.apply(log.address, log.topics, log.data)
}

package com.horizen.account.receipt

import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
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

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: EthereumLog =>
        consensusDataLog.equals(other.consensusDataLog) &&
        new ByteArrayWrapper(transactionHash).equals(new ByteArrayWrapper(other.transactionHash)) &&
        transactionIndex.equals(other.transactionIndex) &&
        new ByteArrayWrapper(blockHash).equals(new ByteArrayWrapper(other.blockHash)) &&
        blockNumber.equals(other.blockNumber) &&
        logIndex == other.logIndex

      case _ => false
    }
  }

  override def hashCode: Int =  {
    var result = consensusDataLog.hashCode()
    result = 31 * result + util.Arrays.hashCode(transactionHash)
    result = 31 * result + Integer.hashCode(transactionIndex)
    result = 31 * result + util.Arrays.hashCode(blockHash)
    result = 31 * result + Integer.hashCode(blockNumber)
    result = 31 * result + Integer.hashCode(logIndex)
    result
  }
}


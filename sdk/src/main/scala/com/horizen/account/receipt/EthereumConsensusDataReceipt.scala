package com.horizen.account.receipt

import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus.ReceiptStatus
import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import com.horizen.account.transaction.EthereumTransaction.EthereumTransactionType
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import org.web3j.rlp._

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util
import scala.collection.mutable.ListBuffer

case class EthereumConsensusDataReceipt(
    transactionType: Int,
    status: Int,
    cumulativeGasUsed: BigInteger,
    logs: Seq[EvmLog],
) {

  /*  From yellow paper
      ----------------------
      the type of the transaction, Rx,
      the status code of the transaction, Rz
      the cumulative gas used in the block containing the transaction receipt as of immediately after the transaction has happened, Ru
      the set of logs created through execution of the transaction, Rl
      and the Bloom filter composed from information in those logs, Rb
   */
  lazy val logsBloom: LogsBloom = LogsBloom.fromEvmLog(logs)

  require(
    transactionType >= EthereumTransactionType.LegacyTxType.ordinal() && transactionType <= EthereumTransactionType.DynamicFeeTxType.ordinal()
  )
  require(
    status == ReceiptStatus.SUCCESSFUL.id || status == ReceiptStatus.FAILED.id
  )

  def getTxType: EthereumTransactionType = {
    transactionType match {
      case 0 =>
        EthereumTransactionType.LegacyTxType
      case 1 =>
        EthereumTransactionType.AccessListTxType
      case 2 =>
        EthereumTransactionType.DynamicFeeTxType
    }
  }

  def getStatus: ReceiptStatus = {
    status match {
      case 0 => ReceiptStatus.FAILED
      case 1 => ReceiptStatus.SUCCESSFUL
    }
  }

  def isStatusOK: Boolean = status == ReceiptStatus.SUCCESSFUL.id

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: EthereumConsensusDataReceipt =>
        transactionType == other.transactionType &&
          status == other.status &&
          cumulativeGasUsed.equals(other.cumulativeGasUsed) &&
          logs.toSet.equals(other.logs.toSet)

      case _ => false
    }
  }

  override def hashCode: Int = {
    var result = Integer.hashCode(transactionType)
    result = 31 * result + Integer.hashCode(status)
    result = 31 * result + cumulativeGasUsed.hashCode()
    for (log <- logs)
      result = 31 * result + log.hashCode()
    result
  }

  override def toString: String = {
    var logsString = "logs{"
    for (log <- logs) {
      logsString = logsString.concat(" " + log.toString)
    }
    logsString = logsString.concat("}")

    val logsBloomStr = BytesUtils.toHexString(logsBloom.getBloomFilter())

    String.format(
      s"EthereumReceipt (receipt consensus data) { txType=$getTxType, status=$getStatus, cumGasUsed=$cumulativeGasUsed, logs=$logsString, logsBloom=$logsBloomStr}"
    )
  }
}

object EthereumConsensusDataReceipt {

  object ReceiptStatus extends Enumeration {
    type ReceiptStatus = Value
    val FAILED, SUCCESSFUL = Value
  }

  def decodeLegacy(rlpData: Array[Byte]): EthereumConsensusDataReceipt = {
    val rlpList = RlpDecoder.decode(rlpData)
    val values = rlpList.getValues.get(0).asInstanceOf[RlpList]
    val postTxState = values.getValues.get(0).asInstanceOf[RlpString].getBytes
    val status = if (postTxState.isEmpty) {
      0
    } else {
      if (postTxState.length != 1 || postTxState(0) != 1) {
        throw new IllegalArgumentException("Invalid rlp postTxState data")
      }
      1
    }
    val cumulativeGasUsed =
      values.getValues.get(1).asInstanceOf[RlpString].asPositiveBigInteger
    val logsBloom = values.getValues.get(2).asInstanceOf[RlpString].getBytes
    val logList = values.getValues.get(3).asInstanceOf[RlpList]
    val logs = new ListBuffer[EvmLog]
    val logsListSize = logList.getValues.size
    if (logsListSize > 0) {
      // loop on list and decode all logs
      for (i <- 0 until logsListSize) {
        val log =
          EvmLogUtils.rlpDecode(logList.getValues.get(i).asInstanceOf[RlpList])
        logs += log
      }
    }
    EthereumConsensusDataReceipt(
      EthereumTransactionType.LegacyTxType.ordinal(),
      status,
      cumulativeGasUsed,
      logs,
    )
  }

  def decodeTyped(
      rt: Int,
      rlpData: Array[Byte]
  ): EthereumConsensusDataReceipt = {
    val r = decodeLegacy(rlpData)
    EthereumConsensusDataReceipt(
      rt,
      r.status,
      r.cumulativeGasUsed,
      r.logs,
    )
  }

  def rlpDecode(rlpData: Array[Byte]): EthereumConsensusDataReceipt = {
    if (rlpData == null || rlpData.length == 0) {
      return null
    }
    // handle tx type
    val b0 = rlpData(0)
    if (b0 == 1 || b0 == 2) {
      decodeTyped(b0, util.Arrays.copyOfRange(rlpData, 1, rlpData.length))
    } else decodeLegacy(rlpData)
  }

  def rlpEncode(r: EthereumConsensusDataReceipt): Array[Byte] = {
    val values = asRlpValues(r)
    val rlpList = new RlpList(values)
    val encoded = RlpEncoder.encode(rlpList)
    if (!(r.getTxType == EthereumTransactionType.LegacyTxType)) {
      // add byte for versioned type support
      ByteBuffer
        .allocate(encoded.length + 1)
        .put(r.getTxType.ordinal().toByte)
        .put(encoded)
        .array
    } else {
      encoded
    }
  }

  def asRlpValues(r: EthereumConsensusDataReceipt): util.List[RlpType] = {
    val result = new util.ArrayList[RlpType]
    val postTxState =
      if (r.status == 1) Array[Byte](1)
      else new Array[Byte](0)
    result.add(RlpString.create(postTxState))
    result.add(RlpString.create(r.cumulativeGasUsed))
    //bloom filters
    result.add(RlpString.create(r.logsBloom.getBloomFilter()))
    // logs
    val rlpLogs = new util.ArrayList[RlpType]
    for (log <- r.logs) {
      rlpLogs.add(new RlpList(EvmLogUtils.asRlpValues(log)))
    }
    result.add(new RlpList(rlpLogs))
    result
  }
}

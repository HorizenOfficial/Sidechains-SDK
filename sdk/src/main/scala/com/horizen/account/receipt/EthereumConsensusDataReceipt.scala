package com.horizen.account.receipt

import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus.ReceiptStatus
import com.horizen.account.receipt.EthereumConsensusDataReceipt.{
  ReceiptStatus,
  createBloomFilter
}
import com.horizen.account.receipt.ReceiptTxType.ReceiptTxType
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import org.web3j.rlp._
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.{ByteBuffer, ByteOrder}
import java.util
import scala.collection.mutable.ListBuffer

case class EthereumConsensusDataReceipt(
    transactionType: Int,
    status: Int,
    cumulativeGasUsed: BigInteger,
    logs: Seq[EvmLog],
    logsBloom: Array[Byte]
) {

  /*  From yellow paper
      ----------------------
      the type of the transaction, Rx,
      the status code of the transaction, Rz
      the cumulative gas used in the block containing the transaction receipt as of immediately after the transaction has happened, Ru
      the set of logs created through execution of the transaction, Rl
      and the Bloom filter composed from information in those logs, Rb
   */

  def this(
      transactionType: Int,
      status: Int,
      cumulativeGasUsed: BigInteger,
      logs: Seq[EvmLog]
  ) {

    this(
      transactionType,
      status,
      cumulativeGasUsed,
      logs,
      createBloomFilter(logs)
    )
  }

  require(
    transactionType >= 0 && transactionType <= ReceiptTxType.DynamicFeeTxType.id
  )
  require(
    status == ReceiptStatus.SUCCESSFUL.id || status == ReceiptStatus.FAILED.id
  )

  def getTxType: ReceiptTxType = {
    transactionType match {
      case 0 =>
        ReceiptTxType.LegacyTxType
      case 1 =>
        ReceiptTxType.AccessListTxType
      case 2 =>
        ReceiptTxType.DynamicFeeTxType
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
          logs.toSet == other.logs.toSet

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

    var logsBloomStr = "null"
    if (logsBloom != null)
      logsBloomStr = BytesUtils.toHexString(logsBloom)

    String.format(
      s"EthereumReceipt (receipt consensus data) { txType=$getTxType, status=$getStatus, cumGasUsed=$cumulativeGasUsed, logs=$logsString, logsBloom=$logsBloomStr}"
    )
  }
}

// TODO check this:
//  The 3 versions of ReceiptTxType are supported by go eth and we have test vectors generated using all of them
//  We are using elsewhere the enum from w3j, which just supports 0 and 2:
//   org/web3j/crypto/transaction/type/TransactionType.java
object ReceiptTxType extends Enumeration {
  type ReceiptTxType = Value
  val LegacyTxType, AccessListTxType, DynamicFeeTxType = Value
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
      ReceiptTxType.LegacyTxType.id,
      status,
      cumulativeGasUsed,
      logs,
      logsBloom
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
      r.logsBloom
    )
  }

  def rlpDecode(rlpData: Array[Byte]): EthereumConsensusDataReceipt = {
    if (rlpData == null || rlpData.length == 0) {
      return null
    }
    // handle tx type
    val b0 = rlpData(0)
    if (b0 == 1 || b0 == 2) {
      val rt = ReceiptTxType(b0).id
      decodeTyped(rt, util.Arrays.copyOfRange(rlpData, 1, rlpData.length))
    } else decodeLegacy(rlpData)
  }

  def rlpEncode(r: EthereumConsensusDataReceipt): Array[Byte] = {
    val values = asRlpValues(r)
    val rlpList = new RlpList(values)
    val encoded = RlpEncoder.encode(rlpList)
    if (!(r.getTxType == ReceiptTxType.LegacyTxType)) {
      // add byte for versioned type support
      ByteBuffer
        .allocate(encoded.length + 1)
        .put(r.getTxType.id.toByte)
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
    result.add(RlpString.create(r.logsBloom))
    // logs
    val rlpLogs = new util.ArrayList[RlpType]
    for (log <- r.logs) {
      rlpLogs.add(new RlpList(EvmLogUtils.asRlpValues(log)))
    }
    result.add(new RlpList(rlpLogs))
    result
  }

  def bytesToShort(byte1: Byte, byte2: Byte): Short = {
    ByteBuffer
      .allocate(2)
      .order(ByteOrder.BIG_ENDIAN)
      .put(byte1)
      .put(byte2)
      .getShort(0)
  }

  def getBloomFilterValues(data: Array[Byte]) = {
    // Copy first 3 pairs of bytes to hashBuffer
    val hashBuffer = Keccak256.hash(data)

    // Calculate byte values
    val v1 = 1 << (hashBuffer(1) & 0x7)
    val v2 = 1 << (hashBuffer(3) & 0x7)
    val v3 = 1 << (hashBuffer(5) & 0x7)

    // Calculate byte indexes
    val i1 =
      256 - ((bytesToShort(hashBuffer(0), hashBuffer(1)) & 0x7ff) >> 3) - 1
    val i2 =
      256 - ((bytesToShort(hashBuffer(2), hashBuffer(3)) & 0x7ff) >> 3) - 1
    val i3 =
      256 - ((bytesToShort(hashBuffer(4), hashBuffer(5)) & 0x7ff) >> 3) - 1

    (i1, v1, i2, v2, i3, v3)
  }

  def addDataToBloomFilter(
      bloomFilter: Array[Byte],
      data: Array[Byte]
  ): Array[Byte] = {
    val (i1, v1, i2, v2, i3, v3) = getBloomFilterValues(data)

    bloomFilter(i1) = (bloomFilter(i1) | v1).toByte
    bloomFilter(i2) = (bloomFilter(i2) | v2).toByte
    bloomFilter(i3) = (bloomFilter(i3) | v3).toByte

    bloomFilter
  }

  def createBloomFilter(evmLog: Seq[EvmLog]): Array[Byte] = {
    var bloomFilter = Array.fill[Byte](256)(0)

    evmLog.foreach(log => {
      bloomFilter = addDataToBloomFilter(bloomFilter, log.address.toBytes)
      log.topics.foreach(topic => {
        bloomFilter = addDataToBloomFilter(bloomFilter, topic.toBytes)
      })
    })

    bloomFilter
  }
}

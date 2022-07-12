package com.horizen.account.receipt

import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus.ReceiptStatus
import com.horizen.account.receipt.EthereumConsensusDataReceipt.{ReceiptStatus, createLogsBloom}
import com.horizen.account.receipt.ReceiptTxType.ReceiptTxType
import com.horizen.utils.BytesUtils
import org.web3j.rlp._

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

case class EthereumConsensusDataReceipt(transactionType: Int,
                                        status: Int,
                                        cumulativeGasUsed: BigInteger,
                                        logs: util.List[EthereumLog],
                                        logsBloom: Array[Byte]) {

  /*  From yellow paper
      ----------------------
      the type of the transaction, Rx,
      the status code of the transaction, Rz
      the cumulative gas used in the block containing the transaction receipt as of immediately after the transaction has happened, Ru
      the set of logs created through execution of the transaction, Rl
      and the Bloom filter composed from information in those logs, Rb
   */

  def this(transactionType: Int,
           status: Int,
           cumulativeGasUsed: BigInteger,
           logs: util.List[EthereumLog]) {

    this(transactionType, status, cumulativeGasUsed, logs, createLogsBloom(logs))
  }

  require(transactionType >= 0 && transactionType <= ReceiptTxType.DynamicFeeTxType.id)
  require(status == ReceiptStatus.SUCCESSFUL.id || status == ReceiptStatus.FAILED.id )

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

  def setLogsBloom(): EthereumConsensusDataReceipt = {
    // creates blooms out of logs content
    val logsBloom = createLogsBloom(this.logs)
    new EthereumConsensusDataReceipt(transactionType, status, cumulativeGasUsed, logs, logsBloom)
  }

  override def toString: String = {
    var logsString = "logs{"
    for (log <- logs.asScala) {
      logsString = logsString.concat(" " + log.toString)
    }
    logsString = logsString.concat("}")

    var logsBloomStr = "null"
    if (logsBloom != null)
      logsBloomStr = BytesUtils.toHexString(logsBloom)

    String.format(s"EthereumReceipt (receipt consensus data) { txType=$getTxType, status=$getStatus, cumGasUsed=$cumulativeGasUsed, logs=$logsString, logsBloom=$logsBloomStr}")
  }
}

// all these versions are supported by go eth; but in w3j apparently only 0 and 2:
//   org/web3j/crypto/transaction/type/TransactionType.java
object ReceiptTxType extends Enumeration {
  type ReceiptTxType = Value
  val LegacyTxType, AccessListTxType, DynamicFeeTxType = Value
}

object EthereumConsensusDataReceipt{

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
    val cumulativeGasUsed = values.getValues.get(1).asInstanceOf[RlpString].asPositiveBigInteger
    val logsBloom = values.getValues.get(2).asInstanceOf[RlpString].getBytes
    val logList = values.getValues.get(3).asInstanceOf[RlpList]
    val logs = new util.ArrayList[EthereumLog](0)
    val logsListSize = logList.getValues.size
    if (logsListSize > 0) {
      // loop on list and decode all logs
      for (i <- 0 until logsListSize) {
        val log = EthereumConsensusDataLog.rlpDecode(logList.getValues.get(i).asInstanceOf[RlpList])
        logs.add(new EthereumLog(log))
      }
    }
    EthereumConsensusDataReceipt(ReceiptTxType.LegacyTxType.id, status, cumulativeGasUsed, logs, logsBloom)
  }


  def decodeTyped(rt: Int, rlpData: Array[Byte]): EthereumConsensusDataReceipt = {
    val r = decodeLegacy(rlpData)
    EthereumConsensusDataReceipt(rt, r.status, r.cumulativeGasUsed, r.logs, r.logsBloom)
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
    }
    else decodeLegacy(rlpData)
  }


  def rlpEncode(r: EthereumConsensusDataReceipt): Array[Byte] = {
    val values = asRlpValues(r)
    val rlpList = new RlpList(values)
    val encoded = RlpEncoder.encode(rlpList)
    if (!(r.getTxType == ReceiptTxType.LegacyTxType)) {
      // add byte for versioned type support
      ByteBuffer.allocate(encoded.length + 1).put(r.getTxType.id.toByte).put(encoded).array
    } else {
      encoded
    }
  }

  def asRlpValues(r: EthereumConsensusDataReceipt): util.List[RlpType] = {
    val result = new util.ArrayList[RlpType]
    val postTxState = if (r.status == 1) Array[Byte](1)
    else new Array[Byte](0)
    result.add(RlpString.create(postTxState))
    result.add(RlpString.create(r.cumulativeGasUsed))
    //bloom filters
    result.add(RlpString.create(r.logsBloom))
    // logs
    val rlpLogs = new util.ArrayList[RlpType]
    for (i <- 0 until r.logs.size) {
      val log = r.logs.get(i)
      rlpLogs.add(new RlpList(EthereumConsensusDataLog.asRlpValues(log.consensusDataLog)))
    }
    result.add(new RlpList(rlpLogs))
    result
  }

  def createLogsBloom(receipt : util.List[EthereumLog]): Array[Byte] = {
    // we can create bloom filter out of a log or out of a receipt log list 
    /* see: https://github.com/ethereum/go-ethereum/blob/55f914a1d764dac4bd37a48173092b1f5c3b186d/core/types/bloom9.go

                // CreateBloom creates a bloom filter out of the give Receipts (+Logs)
                func CreateBloom(receipts Receipts) Bloom {
                    buf := make([]byte, 6)
                    var bin Bloom
                    for _, receipt := range receipts {
                        for _, log := range receipt.Logs {
                            bin.add(log.Address.Bytes(), buf)
                            for _, b := range log.Topics {
                                bin.add(b[:], buf)
                            }
                        }
                    }
                    return bin
                }

                // add is internal version of Add, which takes a scratch buffer for reuse (needs to be at least 6 bytes)
                func (b *Bloom) add(d []byte, buf []byte) {
                    i1, v1, i2, v2, i3, v3 := bloomValues(d, buf)
                    b[i1] |= v1
                    b[i2] |= v2
                    b[i3] |= v3
                }

                // bloomValues returns the bytes (index-value pairs) to set for the given data
                func bloomValues(data []byte, hashbuf []byte) (uint, byte, uint, byte, uint, byte) {
                    sha := hasherPool.Get().(crypto.KeccakState)
                    sha.Reset()
                    sha.Write(data)
                    sha.Read(hashbuf)
                    hasherPool.Put(sha)
                    // The actual bits to flip
                    v1 := byte(1 << (hashbuf[1] & 0x7))
                    v2 := byte(1 << (hashbuf[3] & 0x7))
                    v3 := byte(1 << (hashbuf[5] & 0x7))
                    // The indices for the bytes to OR in
                    i1 := BloomByteLength - uint((binary.BigEndian.Uint16(hashbuf)&0x7ff)>>3) - 1
                    i2 := BloomByteLength - uint((binary.BigEndian.Uint16(hashbuf[2:])&0x7ff)>>3) - 1
                    i3 := BloomByteLength - uint((binary.BigEndian.Uint16(hashbuf[4:])&0x7ff)>>3) - 1

                    return i1, v1, i2, v2, i3, v3
                }

             */
    // TODO shall we use libevm implementation?
    new Array[Byte](256)
  }
}

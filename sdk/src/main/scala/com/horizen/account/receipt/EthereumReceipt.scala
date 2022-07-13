package com.horizen.account.receipt

import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import java.math.BigInteger
import scala.collection.mutable.ListBuffer

case class EthereumReceipt(
                             consensusDataReceipt: EthereumConsensusDataReceipt,
                             transactionHash: Array[Byte],
                             transactionIndex: Int,
                             blockHash: Array[Byte],
                             blockNumber: Int,
                             gasUsed: BigInteger,
                             contractAddress: Array[Byte]) extends BytesSerializable  {
  override type M = EthereumReceipt

  override def serializer: ScorexSerializer[EthereumReceipt] = EthereumReceiptSerializer

  // set a default value for non consensus data, they will be updated by a suited method call
  def this(consensusDataReceipt: EthereumConsensusDataReceipt) {
    this(consensusDataReceipt,
      new Array[Byte](32), -1, new Array[Byte](32), -1, BigInteger.valueOf(-1), new Array[Byte](20))
  }

  def deriveFullLogs() : Seq[EthereumLog] = {
    val logs = this.consensusDataReceipt.logs
    logs.zipWithIndex.map{
      case (log, idx) => EthereumLog.derive(log, transactionHash, transactionIndex, blockHash, blockNumber, idx)
    }
  }

  override def toString: String = {

    var txHashStr : String = "null"
    var blockHashStr: String = "null"
    var contractAddressStr = "null"

    if (transactionHash != null)
      txHashStr = BytesUtils.toHexString(transactionHash)

    if (blockHash != null)
      blockHashStr = BytesUtils.toHexString(blockHash)

    if (contractAddress != null)
      contractAddressStr = BytesUtils.toHexString(contractAddress)

    val infoNonConsensusStr : String =
      String.format(s" - (receipt non consensus data) {txHash=$txHashStr, txIndex=$transactionIndex, blockHash=$blockHashStr, blockNumber=$blockNumber, gasUsed=${gasUsed.toString()}, contractAddress=$contractAddressStr}")

     consensusDataReceipt.toString.concat(infoNonConsensusStr)
  }
}

object EthereumReceiptSerializer extends ScorexSerializer[EthereumReceipt]{

  override def serialize(receipt: EthereumReceipt, writer: Writer): Unit = {
    // consensus data
    writer.putInt(receipt.consensusDataReceipt.transactionType)

    writer.putInt(receipt.consensusDataReceipt.status)

    val cumGasUsedBytes: Array[Byte] = receipt.consensusDataReceipt.cumulativeGasUsed.toByteArray
    writer.putInt(cumGasUsedBytes.length)
    writer.putBytes(cumGasUsedBytes)

    val numberOfLogs = receipt.consensusDataReceipt.logs.size
    writer.putInt(numberOfLogs)
    for (log <- receipt.consensusDataReceipt.logs)
      EvmLogSerializer.serialize(log, writer)

    val bloomBytes: Array[Byte] = receipt.consensusDataReceipt.logsBloom
    writer.putInt(bloomBytes.length)
    writer.putBytes(bloomBytes)

    // derived
    writer.putBytes(receipt.transactionHash)
    writer.putInt(receipt.transactionIndex)
    writer.putBytes(receipt.blockHash)
    writer.putInt(receipt.blockNumber)

    val gasUsedBytes: Array[Byte] = receipt.gasUsed.toByteArray
    writer.putInt(gasUsedBytes.length)
    writer.putBytes(gasUsedBytes)

    writer.putBytes(receipt.contractAddress)

  }

  override def parse(reader: Reader): EthereumReceipt = {
    val transactionType: Int = reader.getInt
    val status: Int = reader.getInt

    val cumGasUsedLength: Int = reader.getInt
    val cumGasUsed: BigInteger = new BigInteger(reader.getBytes(cumGasUsedLength))

    val logs = ListBuffer[EvmLog]()
    val numberOfLogs = reader.getInt
    for (_ <- 0 until numberOfLogs)
      logs += EvmLogSerializer.parse(reader)

    val bloomsLength: Int = reader.getInt
    val blooms: Array[Byte] = reader.getBytes(bloomsLength)

    val receipt: EthereumConsensusDataReceipt = EthereumConsensusDataReceipt(transactionType, status, cumGasUsed, logs, blooms)

    val txHash: Array[Byte] = reader.getBytes(32)
    val txIndex: Int = reader.getInt
    val blockHash: Array[Byte] = reader.getBytes(32)
    val blockNumber: Int = reader.getInt

    val gasUsedLength: Int = reader.getInt
    val gasUsed: BigInteger = new BigInteger(reader.getBytes(gasUsedLength))

    val contractAddress: Array[Byte] = reader.getBytes(20)

    EthereumReceipt(receipt, txHash, txIndex, blockHash, blockNumber, gasUsed, contractAddress)
  }
}

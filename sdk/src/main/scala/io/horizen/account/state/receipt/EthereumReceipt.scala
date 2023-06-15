package io.horizen.account.state.receipt

import io.horizen.evm.Address
import io.horizen.utils.BytesUtils
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger
import java.util
import scala.collection.mutable.ListBuffer

case class EthereumReceipt(
    consensusDataReceipt: EthereumConsensusDataReceipt,
    transactionHash: Array[Byte],
    transactionIndex: Int,
    blockHash: Array[Byte],
    blockNumber: Int,
    gasUsed: BigInteger,
    contractAddress: Option[Address]
) extends BytesSerializable {
  override type M = EthereumReceipt

  override def serializer: SparkzSerializer[EthereumReceipt] = EthereumReceiptSerializer

  override def toString: String = {

    val txHashStr: String = BytesUtils.toHexString(transactionHash)
    val blockHashStr: String = if (blockHash != null) BytesUtils.toHexString(blockHash) else null

    val infoNonConsensusStr: String =
      String.format(
        s" - (receipt non consensus data) {txHash=$txHashStr, txIndex=$transactionIndex, blockHash=$blockHashStr, blockNumber=$blockNumber, gasUsed=${gasUsed
            .toString()}, contractAddress=$contractAddress}"
      )

    consensusDataReceipt.toString.concat(infoNonConsensusStr)
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: EthereumReceipt =>
        consensusDataReceipt.equals(other.consensusDataReceipt) &&
        util.Arrays.equals(transactionHash, other.transactionHash) &&
        transactionIndex.equals(other.transactionIndex) &&
        util.Arrays.equals(blockHash, other.blockHash) &&
        blockNumber.equals(other.blockNumber) &&
        gasUsed.equals(other.gasUsed) &&
        contractAddress.equals(other.contractAddress)

      case _ => false
    }
  }

  override def hashCode: Int = {
    var result = consensusDataReceipt.hashCode()
    result = 31 * result + util.Arrays.hashCode(transactionHash)
    result = 31 * result + Integer.hashCode(transactionIndex)
    result = 31 * result + util.Arrays.hashCode(blockHash)
    result = 31 * result + Integer.hashCode(blockNumber)
    result = 31 * result + gasUsed.hashCode()
    result = 31 * result + contractAddress.hashCode()

    result
  }
}

object EthereumReceiptSerializer extends SparkzSerializer[EthereumReceipt] {

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
      EthereumConsensusDataLog.serialize(log, writer)

    // non consensus data
    writer.putBytes(receipt.transactionHash)
    writer.putInt(receipt.transactionIndex)
    writer.putBytes(receipt.blockHash)
    writer.putInt(receipt.blockNumber)

    val gasUsedBytes: Array[Byte] = receipt.gasUsed.toByteArray
    writer.putInt(gasUsedBytes.length)
    writer.putBytes(gasUsedBytes)

    // optional field
    val addr = receipt.contractAddress.map(_.toBytes).getOrElse(Array.empty)
    writer.putInt(addr.length)
    if (addr.nonEmpty) writer.putBytes(addr)
  }

  override def parse(reader: Reader): EthereumReceipt = {
    val transactionType: Int = reader.getInt
    val status: Int = reader.getInt

    val cumGasUsedLength: Int = reader.getInt
    val cumGasUsed: BigInteger = new BigInteger(reader.getBytes(cumGasUsedLength))

    val logs = ListBuffer[EthereumConsensusDataLog]()
    val numberOfLogs = reader.getInt
    for (_ <- 0 until numberOfLogs)
      logs += EthereumConsensusDataLog.parse(reader)

    val receipt: EthereumConsensusDataReceipt =
      new EthereumConsensusDataReceipt(transactionType, status, cumGasUsed, logs)

    val txHash: Array[Byte] = reader.getBytes(32)
    val txIndex: Int = reader.getInt
    val blockHash: Array[Byte] = reader.getBytes(32)
    val blockNumber: Int = reader.getInt

    val gasUsedLength: Int = reader.getInt
    val gasUsed: BigInteger = new BigInteger(reader.getBytes(gasUsedLength))

    // optional field
    val contractAddressLength = reader.getInt
    val contractAddress =
      if (contractAddressLength == 0) None else Some(new Address(reader.getBytes(contractAddressLength)))

    EthereumReceipt(receipt, txHash, txIndex, blockHash, blockNumber, gasUsed, contractAddress)
  }
}

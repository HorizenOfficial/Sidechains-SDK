package com.horizen.account.receipt

import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait ReceiptFixture {

  def createTestEthereumReceipt(
      txType: Integer,
      num_logs: Integer = 2,
      contractAddressPresence: Boolean = true,
      transactionIndex: Int = 33,
      blockNumber: Int = 22,
      logAddress: Array[Byte] = new Array[Byte](Address.LENGTH),
      txHash: Array[Byte] = new Array[Byte](32),
      blockHash: String = "blockhash"
  ): EthereumReceipt = {
    if (BytesUtils.toHexString(txHash).equals(BytesUtils.toHexString(new Array[Byte](32)))) {
      Random.nextBytes(txHash)
    }

    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(logAddress)

    val contractAddress = if (contractAddressPresence) {
      BytesUtils.fromHexString("1122334455667788990011223344556677889900")
    } else {
      new Array[Byte](0)
    }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
    val receipt = EthereumReceipt(
      consensusDataReceipt,
      txHash,
      transactionIndex,
      Keccak256.hash(blockHash.getBytes).asInstanceOf[Array[Byte]],
      blockNumber,
      BigInteger.valueOf(1234567),
      contractAddress
    )
    receipt
  }

  def createTestEvmLog(addressBytes: Array[Byte] = new Array[Byte](Address.LENGTH)): EvmLog = {
    if (BytesUtils.toHexString(addressBytes).equals(BytesUtils.toHexString(new Array[Byte](Address.LENGTH)))) {
      Random.nextBytes(addressBytes)
    }
    // random address and fixed topics/data
    val address = Address.FromBytes(addressBytes)

    val topics = new Array[Hash](4)
    topics(0) =
      Hash.FromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"))
    topics(1) =
      Hash.FromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"))
    topics(2) =
      Hash.FromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"))
    topics(3) =
      Hash.FromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"))

    val data = BytesUtils.fromHexString("aabbccddeeff")
    new EvmLog(address, topics, data)
  }

  def createTestEthereumConsensusDataReceipt(
      txType: Integer,
      num_logs: Integer,
      logAddress: Array[Byte] = new Array[Byte](Address.LENGTH)
  ): EthereumConsensusDataReceipt = {
    val txHash = new Array[Byte](32)
    Random.nextBytes(txHash)
    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(logAddress)
    new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
  }

}

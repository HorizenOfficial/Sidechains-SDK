package com.horizen.account.receipt


import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import scala.collection.mutable.ListBuffer
import scala.util.Random


trait ReceiptFixture {

    def createTestEvmLog: EvmLog = {
      // random address and fixed topics/data
      val addressBytes = new Array[Byte](Address.LENGTH)
      Random.nextBytes(addressBytes)
      val address = Address.FromBytes(addressBytes)

      val topics = new Array[Hash](4)
      topics(0) = Hash.FromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"))
      topics(1) = Hash.FromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"))
      topics(2) = Hash.FromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"))
      topics(3) = Hash.FromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"))

      val data = BytesUtils.fromHexString("aabbccddeeff")
      new EvmLog(address, topics, data)
    }

  def createTestEthereumReceipt(txType: Integer, num_logs: Integer = 2, contractAddressPresence : Boolean = true): EthereumReceipt = {
    val txHash = new Array[Byte](32)
    Random.nextBytes(txHash)

    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog

    val contractAddress = if (contractAddressPresence) {
      BytesUtils.fromHexString("1122334455667788990011223344556677889900")
    } else {
      new Array[Byte](0)
    }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs, new LogsBloom())
    val receipt = EthereumReceipt(consensusDataReceipt,
      txHash, 33, Keccak256.hash("blockhash".getBytes).asInstanceOf[Array[Byte]], 22,
      BigInteger.valueOf(1234567),
      contractAddress
      )
    receipt
  }

  def createTestEthereumConsensusDataReceipt(txType: Integer, num_logs: Integer): EthereumConsensusDataReceipt = {
    val txHash = new Array[Byte](32)
    Random.nextBytes(txHash)
    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog
    new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs, new LogsBloom())
  }

}

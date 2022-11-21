package com.horizen.account.receipt


import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import scala.collection.mutable.ListBuffer
import scala.util.Random


trait ReceiptFixture {

    def createTestEvmLog(addressBytes: Array[Byte] = null): EvmLog = {
      // random address and fixed topics/data
      var addressBytesTemp = new Array[Byte](Address.LENGTH)
      if(addressBytes == null) {
        Random.nextBytes(addressBytesTemp)
      }
      else {
        addressBytesTemp = addressBytes
      }

      val address = Address.fromBytes(addressBytes)

      val topics = new Array[Hash](4)
      topics(0) = Hash.fromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"))
      topics(1) = Hash.fromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"))
      topics(2) = Hash.fromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"))
      topics(3) = Hash.fromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"))

      val data = BytesUtils.fromHexString("aabbccddeeff")
      new EvmLog(address, topics, data)
    }

  def createTestEthereumReceipt(txType: Integer, num_logs: Integer = 2, contractAddressPresence : Boolean = true, txHash: Array[Byte] = null, address: Array[Byte] = null): EthereumReceipt = {
    var txHashTemp: Array[Byte] = new Array[Byte](32)

    if(txHash != null) {
      txHashTemp = txHash
    }
    else {
      Random.nextBytes(txHashTemp)
    }

    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(address)

    val contractAddress = if (contractAddressPresence) {
      BytesUtils.fromHexString("1122334455667788990011223344556677889900")
    } else {
      new Array[Byte](0)
    }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
    val receipt = EthereumReceipt(consensusDataReceipt,
      txHashTemp, 33, Keccak256.hash("blockhash".getBytes).asInstanceOf[Array[Byte]], 22,
      BigInteger.valueOf(1234567),
      contractAddress
      )
    receipt
  }

  def createTestEthereumConsensusDataReceipt(txType: Integer, num_logs: Integer, address: Array[Byte] = null): EthereumConsensusDataReceipt = {
    val txHash = new Array[Byte](32)
    Random.nextBytes(txHash)
    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(address)
    new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
  }

}

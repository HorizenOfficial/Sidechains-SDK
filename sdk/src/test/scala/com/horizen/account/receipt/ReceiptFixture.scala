package com.horizen.account.receipt

import com.horizen.account.AccountFixture
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait ReceiptFixture extends AccountFixture {

    def createTestEvmLog(address: Option[Address]): EvmLog = {
      val topics = new Array[Hash](4)
      topics(0) = Hash.fromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"))
      topics(1) = Hash.fromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"))
      topics(2) = Hash.fromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"))
      topics(3) = Hash.fromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"))

      val data = BytesUtils.fromHexString("aabbccddeeff")
      new EvmLog(address.getOrElse(randomAddress), topics, data)
    }

  def createTestEthereumReceipt(txType: Integer, num_logs: Integer = 2, contractAddressPresence : Boolean = true, txHash: Option[Array[Byte]] = None, address: Address = Address.ZERO,
                                blockHash: String = "blockhash", blockNumber: Int = 22, transactionIndex: Int = 33): EthereumReceipt = {
    val txHashTemp: Array[Byte] = txHash.getOrElse(randomHash)

    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(Some(address))

    val contractAddress = if (contractAddressPresence) {
      Option(new Address("0x1122334455667788990011223344556677889900"))
    } else {
      None
    }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
    val receipt = EthereumReceipt(
      consensusDataReceipt,
      txHashTemp,
      transactionIndex,
      Keccak256.hash(blockHash.getBytes).asInstanceOf[Array[Byte]],
      blockNumber,
      BigInteger.valueOf(1234567),
      contractAddress
    )
    receipt
  }

  def createTestEthereumConsensusDataReceipt(txType: Integer, num_logs: Integer, address: Address = null): EthereumConsensusDataReceipt = {
    val txHash = new Array[Byte](32)
    Random.nextBytes(txHash)
    val logs = new ListBuffer[EvmLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(Some(address))
    new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
  }

}

package com.horizen.account.receipt

import com.horizen.account.AccountFixture
import com.horizen.utils.BytesUtils
import io.horizen.evm.{Address, Hash}
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer

trait ReceiptFixture extends AccountFixture {

  def createTestEvmLog(address: Option[Address]): EthereumConsensusDataLog = {
    val topics = Array[Hash](
      new Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
      new Hash("0x1111111111111111111111111111111111111111111111111111111111111111"),
      new Hash("0x2222222222222222222222222222222222222222222222222222222222222222"),
      new Hash("0x3333333333333333333333333333333333333333333333333333333333333333"),
    )
    val data = BytesUtils.fromHexString("aabbccddeeff")
    EthereumConsensusDataLog(address.getOrElse(randomAddress), topics, data)
  }

  def createTestEthereumReceipt(
      txType: Integer,
      num_logs: Integer = 2,
      contractAddressPresence: Boolean = true,
      txHash: Option[Array[Byte]] = None,
      address: Address = Address.ZERO,
      blockHash: String = "blockhash",
      blockNumber: Int = 22,
      transactionIndex: Int = 33
  ): EthereumReceipt = {
    val txHashTemp: Array[Byte] = txHash.getOrElse(randomHash)

    val logs = new ListBuffer[EthereumConsensusDataLog]
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
      Keccak256.hash(blockHash.getBytes(StandardCharsets.UTF_8)).asInstanceOf[Array[Byte]],
      blockNumber,
      BigInteger.valueOf(1234567),
      contractAddress
    )
    receipt
  }

  def createTestEthereumConsensusDataReceipt(
      txType: Integer,
      num_logs: Integer,
      address: Address = null
  ): EthereumConsensusDataReceipt = {
    val logs = new ListBuffer[EthereumConsensusDataLog]
    for (_ <- 1 to num_logs)
      logs += createTestEvmLog(Some(address))
    new EthereumConsensusDataReceipt(txType, 1, BigInteger.valueOf(1000), logs)
  }

}

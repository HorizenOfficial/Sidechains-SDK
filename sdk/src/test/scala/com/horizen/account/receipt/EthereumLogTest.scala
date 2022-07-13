package com.horizen.account.receipt

import com.horizen.account.receipt.EthereumLogTest.createTestEthereumConsensusDataLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import scorex.crypto.hash.Keccak256

class EthereumLogTest
  extends JUnitSuite
    with MockitoSugar
{

  @Test def receiptSimpleEncodeDecodeTest(): Unit = {
    val ethereumLog = createTestEthereumConsensusDataLog
    println(ethereumLog)
    val encodedLog = EthereumConsensusDataLog.rlpEncode(ethereumLog)
    println(BytesUtils.toHexString(encodedLog))
    // read what you write
    val dataBytes = encodedLog
    val decodedConsensusDataLog = EthereumConsensusDataLog.rlpDecode(dataBytes)
    System.out.println(decodedConsensusDataLog)
    assertEquals(
      BytesUtils.toHexString(ethereumLog.address.toBytes),
      BytesUtils.toHexString(decodedConsensusDataLog.address.toBytes))
  }

}

object EthereumLogTest {

  def createTestEthereumLog: EthereumLog = {
    // add also non consensus data info, not rlp handled
    val log: EthereumLog = EthereumLog(
      createTestEthereumConsensusDataLog,
      Keccak256.hash("txhash".getBytes).asInstanceOf[Array[Byte]],
      1, // txIndex
      Keccak256.hash("blockhash".getBytes).asInstanceOf[Array[Byte]],
      100, // blockNumber
      1, // logIndex
      0 // removed
    )
    log
  }

  def createTestEthereumConsensusDataLog: EthereumConsensusDataLog = {
    val address = Address.FromBytes(BytesUtils.fromHexString("1122334455667788990011223344556677889900"))
    val topics = new Array[Hash](4)
    topics(0) = Hash.FromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"))
    topics(1) = Hash.FromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"))
    topics(2) = Hash.FromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"))
    topics(3) = Hash.FromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"))
    val data = BytesUtils.fromHexString("aabbccddeeff")
    EthereumConsensusDataLog(address, topics, data)
  }

}

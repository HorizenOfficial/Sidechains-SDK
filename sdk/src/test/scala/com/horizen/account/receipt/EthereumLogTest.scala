package com.horizen.account.receipt

import com.horizen.account.receipt.EthereumLogTest.createTestEthereumConsensusDataLog
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.util.Random

class EthereumLogTest
  extends JUnitSuite
    with MockitoSugar
{

  @Test def receiptSimpleEncodeDecodeTest(): Unit = {
    val ethereumLog = createTestEthereumConsensusDataLog
    //println(ethereumLog)
    val encodedLog = EthereumConsensusDataLog.rlpEncode(ethereumLog)
    //println(BytesUtils.toHexString(encodedLog))
    // read what you write
    val dataBytes = encodedLog
    val decodedConsensusDataLog = EthereumConsensusDataLog.rlpDecode(dataBytes)
    //println(decodedConsensusDataLog)
    assertEquals(
      BytesUtils.toHexString(ethereumLog.address.toBytes),
      BytesUtils.toHexString(decodedConsensusDataLog.address.toBytes))
  }

}

object EthereumLogTest {

  def createTestEthereumConsensusDataLog: EvmLog = {
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

}

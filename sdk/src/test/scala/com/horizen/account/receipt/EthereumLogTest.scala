package com.horizen.account.receipt


import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

class EthereumLogTest
  extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture
{

  @Test def receiptSimpleEncodeDecodeTest(): Unit = {
    val ethereumLog = createTestEvmLog
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
    assertEquals(ethereumLog, decodedConsensusDataLog)
    assertEquals(ethereumLog.hashCode(), decodedConsensusDataLog.hashCode())
  }

}



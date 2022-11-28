package com.horizen.account.receipt


import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

class EvmLogTest
  extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture
{

  @Test def receiptSimpleEncodeDecodeTest(): Unit = {
    val evmLog = createTestEvmLog(None)
    //println(ethereumLog)
    val encodedLog = EvmLogUtils.rlpEncode(evmLog)
    //println(BytesUtils.toHexString(encodedLog))
    // read what you write
    val dataBytes = encodedLog
    val decodedConsensusDataLog = EvmLogUtils.rlpDecode(dataBytes)
    //println(decodedConsensusDataLog)
    assertEquals(
      BytesUtils.toHexString(evmLog.address.toBytes),
      BytesUtils.toHexString(decodedConsensusDataLog.address.toBytes))
    assertEquals(evmLog, decodedConsensusDataLog)
    assertEquals(evmLog.hashCode(), decodedConsensusDataLog.hashCode())
  }

}



package com.horizen.account.state.receipt

import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

class EthereumConsensusDataLogTest
  extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture
{

  @Test def receiptSimpleEncodeDecodeTest(): Unit = {
    val evmLog = createTestLog(None)
    //println(ethereumLog)
    val encodedLog = EthereumConsensusDataLog.rlpEncode(evmLog)
    //println(BytesUtils.toHexString(encodedLog))
    // read what you write
    val dataBytes = encodedLog
    val decodedConsensusDataLog = EthereumConsensusDataLog.rlpDecode(dataBytes)
    //println(decodedConsensusDataLog)
    assertEquals(
      BytesUtils.toHexString(evmLog.address.toBytes),
      BytesUtils.toHexString(decodedConsensusDataLog.address.toBytes))
    assertEquals(evmLog, decodedConsensusDataLog)
    assertEquals(evmLog.hashCode(), decodedConsensusDataLog.hashCode())
  }

}

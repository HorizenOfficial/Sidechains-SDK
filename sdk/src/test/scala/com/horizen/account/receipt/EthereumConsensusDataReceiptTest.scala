package com.horizen.account.receipt

import com.horizen.account.receipt.LogsBloom.BLOOM_FILTER_LENGTH
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import scorex.crypto.hash.Keccak256

class EthereumConsensusDataReceiptTest
    extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture {

  @Test def bloomFilterTest(): Unit = {
    val data = BytesUtils.fromHexString(
      "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    )

    val bloomLogs = new LogsBloom()
    bloomLogs.addBytesToBloomFilter(data)

    val bloomFilterExpected = Array.fill[Byte](LogsBloom.BLOOM_FILTER_LENGTH)(0)
    bloomFilterExpected(75) = 0x8
    bloomFilterExpected(195) = 0x2
    bloomFilterExpected(123) = 0x10

    assertArrayEquals(bloomLogs.getBloomFilter(), bloomFilterExpected)
  }

  @Test def bloomFilterGethTest(): Unit = {
    val positive = Array("testtest", "test", "hallo", "other")
    val negative = Array("tes", "lo")

    val bloomLog = new LogsBloom()

    positive.foreach(s => bloomLog.addBytesToBloomFilter(s.getBytes()))
    positive.foreach(s => assert(bloomLog.contains(s.getBytes())))
    negative.foreach(s => assert(!bloomLog.contains(s.getBytes())))

    val bloomLog2 = new LogsBloom()
    bloomLog2.setBytes(bloomLog.getBloomFilter())

    positive.foreach(s => assert(bloomLog2.contains(s.getBytes())))
    negative.foreach(s => assert(!bloomLog2.contains(s.getBytes())))

    val bloomLog3 = new LogsBloom()

    positive.foreach(s => assert(!bloomLog3.contains(s.getBytes())))
    negative.foreach(s => assert(!bloomLog3.contains(s.getBytes())))
  }

  @Test def bloomFilterGethExtensiveTest(): Unit = {
    val exp = BytesUtils.fromHexString("c8d3ca65cdb4874300a9e39475508f23ed6da09fdbc487f89a2dcf50b09eb263")
    val bloomLog = new LogsBloom()

    for(i <- 0 until 100) {
      bloomLog.addBytesToBloomFilter(s"xxxxxxxxxx data $i yyyyyyyyyyyyyy".getBytes())
    }

    val bloomFilterHash = Keccak256.hash(bloomLog.getBloomFilter())
    assertArrayEquals(exp, bloomFilterHash)

    val bloomLog2 = new LogsBloom()
    bloomLog2.setBytes(bloomLog.getBloomFilter())

    val bloomFilterHash2 = Keccak256.hash(bloomLog2.getBloomFilter())
    assertArrayEquals(bloomFilterHash, bloomFilterHash2)
  }

  @Test def bloomFilterEmptyTest(): Unit = {
    val bloomLog = new LogsBloom()
    val bloomFilter = bloomLog.getBloomFilter()

    assertArrayEquals(bloomFilter, Array.fill[Byte](BLOOM_FILTER_LENGTH)(0))
  }

}

package com.horizen.account.receipt

import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.crypto.hash.Keccak256

import java.nio.charset.StandardCharsets

class BloomTest
    extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture {

  @Test def bloomFilterTest(): Unit = {
    val data = BytesUtils.fromHexString(
      "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    )

    val bloomLogs = new Bloom()
    bloomLogs.add(data)

    val gethReference = BytesUtils.fromHexString("00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
    assertArrayEquals("should match geth implementation", gethReference, bloomLogs.getBytes)

    val bloomFilterExpected = Array.fill[Byte](Bloom.BLOOM_BYTE_LENGTH)(0)
    bloomFilterExpected(75) = 0x8
    bloomFilterExpected(195) = 0x2
    bloomFilterExpected(123) = 0x10

    assertArrayEquals(bloomLogs.getBytes, bloomFilterExpected)
  }

  @Test def bloomFilterGethTest(): Unit = {
    val positive = Array("testtest", "test", "hallo", "other")
    val negative = Array("tes", "lo")

    val bloomLog = new Bloom()

    positive.foreach(s => bloomLog.add(s.getBytes(StandardCharsets.UTF_8)))
    positive.foreach(s => assert(bloomLog.test(s.getBytes(StandardCharsets.UTF_8))))
    negative.foreach(s => assert(!bloomLog.test(s.getBytes(StandardCharsets.UTF_8))))

    val bloomLog2 = new Bloom()
    bloomLog2.merge(bloomLog)

    positive.foreach(s => assert(bloomLog2.test(s.getBytes(StandardCharsets.UTF_8))))
    negative.foreach(s => assert(!bloomLog2.test(s.getBytes(StandardCharsets.UTF_8))))

    val bloomLog3 = new Bloom()

    positive.foreach(s => assert(!bloomLog3.test(s.getBytes(StandardCharsets.UTF_8))))
    negative.foreach(s => assert(!bloomLog3.test(s.getBytes(StandardCharsets.UTF_8))))
  }

  @Test def bloomFilterGethExtensiveTest(): Unit = {
    val exp = BytesUtils.fromHexString("c8d3ca65cdb4874300a9e39475508f23ed6da09fdbc487f89a2dcf50b09eb263")
    val bloomLog = new Bloom()

    for(i <- 0 until 100) {
      bloomLog.add(s"xxxxxxxxxx data $i yyyyyyyyyyyyyy".getBytes(StandardCharsets.UTF_8))
    }

    val bloomFilterHash = Keccak256.hash(bloomLog.getBytes)
    assertArrayEquals(exp, bloomFilterHash)

    val bloomLog2 = new Bloom()
    bloomLog2.merge(bloomLog)

    val bloomFilterHash2 = Keccak256.hash(bloomLog2.getBytes)
    assertArrayEquals(bloomFilterHash, bloomFilterHash2)
  }

  @Test def bloomFilterEmptyTest(): Unit = {
    val bloomLog = new Bloom()
    val bloomFilter = bloomLog.getBytes

    assertArrayEquals(bloomFilter, Array.fill[Byte](Bloom.BLOOM_BYTE_LENGTH)(0))
  }

  @Test
  def bloomFiltersSameBitsDifferentPositions(): Unit = {
    val hexString = "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    val data1 = BytesUtils.fromHexString(hexString)
    val data2 = BytesUtils.fromHexString(hexString.reverse)

    val bloomLogs1 = new Bloom()
    bloomLogs1.add(data1)

    val bloomLogs2 = new Bloom()
    bloomLogs2.add(data2)

    assertNotEquals(bloomLogs2, bloomLogs1);
  }

  @Test
  def bloomFilterAuxiliaryConstructor(): Unit = {
    val hexString = "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    val data = BytesUtils.fromHexString(hexString)

    val bloomLogs = new Bloom(data)

    assertArrayEquals(data, bloomLogs.getBytes)
  }
}

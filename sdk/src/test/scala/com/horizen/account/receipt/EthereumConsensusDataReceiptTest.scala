package com.horizen.account.receipt

import com.horizen.account.receipt.EthereumConsensusDataReceipt.addDataToBloomFilter
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

class EthereumConsensusDataReceiptTest
    extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture {

  @Test def bloomFilterTest(): Unit = {
    val BLOOM_FILTER_LENGTH = 256
    var bloomFilter = Array.fill[Byte](BLOOM_FILTER_LENGTH)(0)
    val data = BytesUtils.fromHexString(
      "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    )

    bloomFilter = addDataToBloomFilter(bloomFilter, data)

    val bloomFilterExpected = Array.fill[Byte](BLOOM_FILTER_LENGTH)(0)
    bloomFilterExpected(75) = 0x8
    bloomFilterExpected(195) = 0x2
    bloomFilterExpected(123) = 0x10

    assertArrayEquals(bloomFilter, bloomFilterExpected)
  }

}

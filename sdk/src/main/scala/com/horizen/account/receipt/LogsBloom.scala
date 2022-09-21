package com.horizen.account.receipt

import scorex.crypto.hash.Keccak256
import com.horizen.account.receipt.LogsBloom.BLOOM_FILTER_LENGTH
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils

class LogsBloom() {
  private var bloomFilter = Array.fill[Byte](BLOOM_FILTER_LENGTH)(0)

  def this(bloomFilter: Array[Byte]) = {
    this()

    require(bloomFilter.length == BLOOM_FILTER_LENGTH)
    this.bloomFilter = bloomFilter.clone()
  }

  def addLogToBloomFilter(log: EvmLog): Unit = {
    addBytesToBloomFilter(log.address.toBytes)
    log.topics.foreach(topic => addBytesToBloomFilter(topic.toBytes))
  }

  def addBytesToBloomFilter(data: Array[Byte]): Unit = {
    val (bloomFilterIndexes, bloomFilterValues) = getBloomFilterValues(data)

    bloomFilterIndexes.zipWithIndex
      .foreach({ case (bloomFilterIndex, i) =>
        bloomFilter(bloomFilterIndex) = (bloomFilter(bloomFilterIndex) | bloomFilterValues(i)).toByte
      })
  }

  def addBloomFilter(bloomFilter: LogsBloom): Unit = {
    val bloomBytes = bloomFilter.getBloomFilter()

    this.bloomFilter.zipWithIndex
      .foreach({ case (bloomByte, i) =>
        this.bloomFilter(i) = (bloomByte | bloomBytes(i)).toByte
      })
  }

  def contains(data: Array[Byte]): Boolean = {
    val (dataIndexes, dataValues) = getBloomFilterValues(data)

    dataIndexes.zipWithIndex.foreach({ case (dataIndex, i) =>
      if (bloomFilter(dataIndex).&(dataValues(i)) != dataValues(i)) {
        return false
      }
    })

    true
  }

  private def getBloomFilterValues(data: Array[Byte]): (Array[Int], Array[Int]) = {
    val hashBuffer = Keccak256.hash(data)
    val bloomFilterIndexes: Array[Int] = new Array[Int](3)

    bloomFilterIndexes(0) =
      BLOOM_FILTER_LENGTH - ((BytesUtils.getShort(Array[Byte](hashBuffer(0), hashBuffer(1)), 0) & 0x7ff) >> 3) - 1

    bloomFilterIndexes(1) =
      BLOOM_FILTER_LENGTH - ((BytesUtils.getShort(Array[Byte](hashBuffer(2), hashBuffer(3)), 0) & 0x7ff) >> 3) - 1

    bloomFilterIndexes(2) =
      BLOOM_FILTER_LENGTH - ((BytesUtils.getShort(Array[Byte](hashBuffer(4), hashBuffer(5)), 0) & 0x7ff) >> 3) - 1

    val bloomFilterValues: Array[Int] =
      Array(1, 3, 5).map(e => 1 << (hashBuffer(e) & 0x7))

    (bloomFilterIndexes, bloomFilterValues)
  }

  def getBloomFilter(): Array[Byte] = {
    bloomFilter.clone()
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case l: LogsBloom => (this.bloomFilter diff l.getBloomFilter()).isEmpty
      case _ => false
    }
  }
}

object LogsBloom {
  val BLOOM_FILTER_LENGTH = 256

  def fromEvmLog(evmLogs: Seq[EvmLog]): LogsBloom = {
    val logsBloom = new LogsBloom()

    evmLogs.foreach(log => {
      logsBloom.addLogToBloomFilter(log)
    })

    logsBloom
  }

  def fromEthereumReceipt(ethereumReceipts: Seq[EthereumReceipt]): LogsBloom = {
    val logsBloom = new LogsBloom()

    ethereumReceipts.foreach(receipt => {
      receipt.consensusDataReceipt.logs.foreach(log => {
        logsBloom.addLogToBloomFilter(log)
      })
    })

    logsBloom
  }
}

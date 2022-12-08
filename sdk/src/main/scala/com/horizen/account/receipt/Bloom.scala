package com.horizen.account.receipt

import com.horizen.account.receipt.Bloom.BLOOM_BYTE_LENGTH
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import scorex.crypto.hash.Keccak256
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

/**
 * Bloom represents a 2048 bit bloom filter.
 */
class Bloom() extends BytesSerializable {
  private var filter = Array.fill[Byte](BLOOM_BYTE_LENGTH)(0)

  override type M = Bloom
  override def serializer: SparkzSerializer[Bloom] = BloomSerializer

  /**
   * Create a bloom filter from the given byte array, use as-is.
   * @param filter raw 256 bytes bitmask of a bloom filter
   */
  def this(filter: Array[Byte]) = {
    this()
    require(filter.length == BLOOM_BYTE_LENGTH)
    this.filter = filter.clone()
  }

  /**
   * Add given data to the bloom filter.
   * @param data
   *   raw data to hash and add to the filter
   */
  def add(data: Array[Byte]): Unit = {
    getBloomValues(data)
      .foreach({ case (index, value) =>
        filter(index) = (filter(index) | value).toByte
      })
  }

  /**
   * Add given log to the bloom filter. This will add the logs address and all topics to the filter.
   * @param log
   *   the log to add to the filter
   */
  def add(log: EvmLog): Unit = {
    add(log.address.toBytes)
    log.topics.foreach(topic => add(topic.toBytes))
  }

  /**
   * Merge the given bloom filter into this filter.
   * @param bloom
   *   instance of another bloom filter.
   */
  def merge(bloom: Bloom): Unit = {
    bloom.filter.zipWithIndex
      .foreach({ case (bloomByte, i) =>
        filter(i) = (filter(i) | bloomByte).toByte
      })
  }

  /**
   * Checks if the given data is present in the bloom filter.
   * @note
   *   This test might give false positives, but will never give false negatives. If the test fails the given data is
   *   definitely not present, if it succeeds, there is a high chance of data being present.
   * @param data
   *   raw data to test for presence
   * @return
   *   false if data is not present in the filter, true if it likely, see note
   */
  def test(data: Array[Byte]): Boolean = {
    getBloomValues(data)
      .exists({ case (index, value) =>
        (filter(index) & value) == value
      })
  }

  private def getBloomValues(data: Array[Byte]): Array[(Int, Int)] = {
    val hashBuffer = Keccak256.hash(data)

    val values = Array(
      1 << (hashBuffer(1) & 0x7),
      1 << (hashBuffer(3) & 0x7),
      1 << (hashBuffer(5) & 0x7),
    )

    val indices = Array(
      BLOOM_BYTE_LENGTH - ((BytesUtils.getShort(hashBuffer, 0) & 0x7ff) >> 3) - 1,
      BLOOM_BYTE_LENGTH - ((BytesUtils.getShort(hashBuffer, 2) & 0x7ff) >> 3) - 1,
      BLOOM_BYTE_LENGTH - ((BytesUtils.getShort(hashBuffer, 4) & 0x7ff) >> 3) - 1,
    )

    indices.zip(values)
  }

  /**
   * Return the raw 256 bytes bitmask of this bloom filter.
   * @return 256 byte bitmask
   */
  def getBytes: Array[Byte] = {
    filter.clone()
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case l: Bloom => this.filter.sameElements(l.filter)
      case _ => false
    }
  }
}

object Bloom {
  val BLOOM_BYTE_LENGTH: Int = 256
  val BLOOM_BIT_LENGTH: Int = 8 * BLOOM_BYTE_LENGTH

  def fromLogs(evmLogs: Seq[EvmLog]): Bloom = {
    val filter = new Bloom()
    evmLogs.foreach(filter.add)
    filter
  }

  def fromReceipts(receipts: Seq[EthereumReceipt]): Bloom = {
    val filter = new Bloom()
    receipts.map(_.consensusDataReceipt.logsBloom).foreach(filter.merge)
    filter
  }
}

object BloomSerializer extends SparkzSerializer[Bloom] {
  override def serialize(obj: Bloom, w: Writer): Unit = {
    w.putBytes(obj.getBytes)
  }

  override def parse(r: Reader): Bloom = {
    val bloomFilter = r.getBytes(BLOOM_BYTE_LENGTH)
    new Bloom(bloomFilter)
  }
}

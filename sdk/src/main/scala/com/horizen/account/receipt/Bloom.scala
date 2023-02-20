package com.horizen.account.receipt

import com.horizen.account.receipt.Bloom.BLOOM_BYTE_LENGTH
import com.horizen.utils.BytesUtils
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.crypto.hash.Keccak256
import sparkz.util.serialization.{Reader, Writer}

import java.util

/**
 * Bloom represents a 2048 bit bloom filter.
 */
class Bloom(private val filter: Array[Byte]) extends BytesSerializable {
  require(filter.length == BLOOM_BYTE_LENGTH)

  override type M = Bloom
  override def serializer: SparkzSerializer[Bloom] = BloomSerializer

  // this is essentially a default value for the constructor argument, but this also works in Java
  def this() = this(Array.fill[Byte](BLOOM_BYTE_LENGTH)(0))

  /**
   * Add given data to the bloom filter.
   * @param data
   *   raw data to hash and add to the filter
   */
  def add(data: Array[Byte]): Unit = {
    for ((value, index) <- getBloomValues(data)) {
      filter(index) = (filter(index) | value).toByte
    }
  }

  /**
   * Merge the given bloom filter into this filter.
   * @param bloom
   *   instance of another bloom filter.
   */
  def merge(bloom: Bloom): Unit = {
    for ((value, index) <- bloom.filter.zipWithIndex) {
      filter(index) = (filter(index) | value).toByte
    }
  }

  /**
   * Checks if the given data is present in the bloom filter.
   * @note
   *   This test might give false positives, but will never give false negatives. If the test fails the given data is
   *   definitely not present, if it succeeds, there is a high chance of data being present.
   * @param data
   *   raw data to test for presence
   * @return
   *   false if data is not present in the filter, true if it likely is, see note
   */
  def test(data: Array[Byte]): Boolean = {
    getBloomValues(data)
      .exists({ case (value, index) =>
        (filter(index) & value) == value
      })
  }

  private def getBloomValues(data: Array[Byte]): Array[(Int, Int)] = {
    val hashBuffer = Keccak256.hash(data)

    val values = Array(
      1 << (hashBuffer(1) & 0x7),
      1 << (hashBuffer(3) & 0x7),
      1 << (hashBuffer(5) & 0x7)
    )

    val indices = Array(
      BLOOM_BYTE_LENGTH - ((BytesUtils.getShort(hashBuffer, 0) & 0x7ff) >> 3) - 1,
      BLOOM_BYTE_LENGTH - ((BytesUtils.getShort(hashBuffer, 2) & 0x7ff) >> 3) - 1,
      BLOOM_BYTE_LENGTH - ((BytesUtils.getShort(hashBuffer, 4) & 0x7ff) >> 3) - 1
    )

    values.zip(indices)
  }

  /**
   * Return the raw 256 bytes bitmask of this bloom filter.
   * @return
   *   256 bytes bitmask
   */
  def getBytes: Array[Byte] = {
    filter.clone()
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Bloom => filter.sameElements(other.filter)
      case _ => false
    }
  }

  override def hashCode(): Int = {
    util.Arrays.hashCode(filter)
  }
}

object Bloom {
  val BLOOM_BYTE_LENGTH: Int = 256
  val BLOOM_BIT_LENGTH: Int = 8 * BLOOM_BYTE_LENGTH

  /**
   * Create a bloom filter from the given byte array.
   *
   * @param filter
   *   raw 256 bytes bitmask of a bloom filter
   */
  def apply(filter: Array[Byte]): Bloom = {
    new Bloom(filter.clone())
  }

  def fromLogs(evmLogs: Seq[EthereumConsensusDataLog]): Bloom = {
    val filter = new Bloom()
    for (log <- evmLogs) {
      filter.add(log.address.toBytes)
      for (topic <- log.topics) {
        filter.add(topic.toBytes)
      }
    }
    filter
  }

  def fromReceipts(receipts: Seq[EthereumConsensusDataReceipt]): Bloom = {
    val filter = new Bloom()
    receipts.map(_.logsBloom).foreach(filter.merge)
    filter
  }
}

object BloomSerializer extends SparkzSerializer[Bloom] {
  override def serialize(obj: Bloom, w: Writer): Unit = {
    w.putBytes(obj.getBytes)
  }

  override def parse(r: Reader): Bloom = {
    new Bloom(r.getBytes(BLOOM_BYTE_LENGTH))
  }
}

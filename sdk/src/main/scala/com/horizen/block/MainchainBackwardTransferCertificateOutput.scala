package com.horizen.block

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class MainchainBackwardTransferCertificateOutput
  (outputBytes: Array[Byte],
   pubKeyHash: Array[Byte],
   amount: Long)
{

  def size: Int = outputBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(outputBytes))

}

object MainchainBackwardTransferCertificateOutput {
  def parse(outputBytes: Array[Byte], offset: Int): MainchainBackwardTransferCertificateOutput = {

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(outputBytes, currentOffset)
    currentOffset += 8

    val pubKeyHash: Array[Byte] = BytesUtils.reverseBytes(outputBytes.slice(currentOffset, currentOffset + 20))
    currentOffset += 20

    new MainchainBackwardTransferCertificateOutput(outputBytes.slice(offset, currentOffset), pubKeyHash, amount)
  }
}

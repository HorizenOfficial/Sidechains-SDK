package com.horizen.block

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class MainchainBackwardTransferCertificateOutput
  (outputBytes: Array[Byte],
   pubKeyHash: Array[Byte],
   amount: Long,
   fee: Long = 0)
{

  def originalAmount: Long = {
    amount + fee
  }

  def size: Int = outputBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(outputBytes))

}

object MainchainBackwardTransferCertificateOutput {
  def parse(outputBytes: Array[Byte], offset: Int): MainchainBackwardTransferCertificateOutput = {

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(outputBytes, currentOffset)
    currentOffset += 8

    val scriptLength: VarInt = BytesUtils.getVarInt(outputBytes, currentOffset)
    currentOffset += scriptLength.size()

    val pubKeyHash: Array[Byte] = BytesUtils.reverseBytes(outputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += scriptLength.value().intValue()

    new MainchainBackwardTransferCertificateOutput(outputBytes.slice(offset, currentOffset), pubKeyHash, amount)
  }
}

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
  extends BytesSerializable
{
  override type M = MainchainBackwardTransferCertificateOutput

  override def serializer: ScorexSerializer[MainchainBackwardTransferCertificateOutput] = MainchainBackwardTransferCertificateOutputSerializer

  def originalAmount: Long = {
    amount + fee
  }

  def size: Int = outputBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(outputBytes))

  override def bytes: Array[Byte] =
    Bytes.concat(Ints.toByteArray(outputBytes.length),
      outputBytes,
      Ints.toByteArray(pubKeyHash.length),
      pubKeyHash,
      Longs.toByteArray(amount),
      Longs.toByteArray(fee))
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

  def parseBytes(bytes: Array[Byte]): MainchainBackwardTransferCertificateOutput = {
    var offset: Int = 0

    val outputBytesSize = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset +=4

    val outputBytes = bytes.slice(offset, offset + outputBytesSize)
    offset += outputBytesSize

    val pubKeyHashSize = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset += 4

    val pubKeyHash = bytes.slice(offset, offset + pubKeyHashSize)
    offset += pubKeyHashSize

    val amount = Longs.fromByteArray(bytes.slice(offset, offset + 8))
    offset += 8

    val fee = Longs.fromByteArray(bytes.slice(offset, offset + 8))
    offset += 8

    new MainchainBackwardTransferCertificateOutput(outputBytes, pubKeyHash, amount, fee)
  }
}

object MainchainBackwardTransferCertificateOutputSerializer
  extends ScorexSerializer[MainchainBackwardTransferCertificateOutput]
{
  override def serialize(output: MainchainBackwardTransferCertificateOutput, w: Writer): Unit = {
    w.putBytes(output.bytes)
  }

  override def parse(r: Reader): MainchainBackwardTransferCertificateOutput = {
    MainchainBackwardTransferCertificateOutput.parseBytes(r.getBytes(r.remaining))
  }
}

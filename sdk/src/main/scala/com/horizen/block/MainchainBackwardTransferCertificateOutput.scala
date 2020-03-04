package com.horizen.block

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

object MainchainBackwardTransferCertificateOutputSerializer
  extends ScorexSerializer[MainchainBackwardTransferCertificateOutput]
{
  override def serialize(output: MainchainBackwardTransferCertificateOutput, w: Writer): Unit = {
    w.putLong(output.amount)
    w.putLong(output.fee)
    w.putInt(output.pubKeyHash.length)
    w.putBytes(output.pubKeyHash)
  }

  override def parse(r: Reader): MainchainBackwardTransferCertificateOutput = {
    val amount = r.getLong()
    val fee = r.getLong()
    val keySize = r.getInt()
    val pubKeyHash = r.getBytes(keySize)
    new MainchainBackwardTransferCertificateOutput(pubKeyHash, pubKeyHash, amount, fee)
  }
}

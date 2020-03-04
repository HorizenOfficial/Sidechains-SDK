package com.horizen.block

import com.horizen.utils.{BytesUtils, ListSerializer, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

class MainchainBackwardTransferCertificate
  (val certificateBytes: Array[Byte],
   val version: Int,
   val sidechainId: Array[Byte],
   val epochNumber: Int,
   val endEpochBlockHash: Array[Byte],
   val totalAmount: Long,
   val outputs: Seq[MainchainBackwardTransferCertificateOutput])
  extends BytesSerializable
{
  override type M = MainchainBackwardTransferCertificate

  override def serializer: ScorexSerializer[MainchainBackwardTransferCertificate] = MainchainBackwardTransferCertificateSerializer

  def size: Int = certificateBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certificateBytes))

}

object MainchainBackwardTransferCertificate {
  def parse(certificateBytes: Array[Byte], offset: Int) : MainchainBackwardTransferCertificate = {

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val epochNumber: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val endEpochBlockHash: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val totalAmount: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

    val outputCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += outputCount.size()

    var outputs: Seq[MainchainBackwardTransferCertificateOutput] = Seq[MainchainBackwardTransferCertificateOutput]()

    while(outputs.size < outputCount.value()) {
      val o: MainchainBackwardTransferCertificateOutput = MainchainBackwardTransferCertificateOutput.parse(certificateBytes, currentOffset)
      outputs = outputs :+ o
      currentOffset += o.size
    }

    val ccoutCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += ccoutCount.size()

    val nounce: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val totalFee: Long = totalAmount - outputs.map(_.amount).sum
    val fee = totalFee / outputs.size

    val outputsWithFee = outputs.map(o => MainchainBackwardTransferCertificateOutput(o.outputBytes, o.pubKeyHash, o.amount, fee))

    new MainchainBackwardTransferCertificate(certificateBytes.slice(offset, currentOffset), version,
      sidechainId, epochNumber, endEpochBlockHash, totalAmount, outputsWithFee)

  }
}

object MainchainBackwardTransferCertificateSerializer
  extends ScorexSerializer[MainchainBackwardTransferCertificate]
{

  override def serialize(certificate: MainchainBackwardTransferCertificate, w: Writer): Unit = {
    w.putInt(certificate.certificateBytes.length)
    w.putBytes(certificate.certificateBytes)
}

  override def parse(r: Reader): MainchainBackwardTransferCertificate = {
    val certificateBytesSize = r.getInt()
    val certificateBytes = r.getBytes(certificateBytesSize)
    MainchainBackwardTransferCertificate.parse(certificateBytes, 0)
  }

}

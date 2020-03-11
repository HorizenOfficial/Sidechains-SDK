package com.horizen.block

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.utils.{BytesUtils, ListSerializer, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._

case class MainchainBackwardTransferCertificate
  (certificateBytes: Array[Byte],
   version: Int,
   sidechainId: Array[Byte],
   epochNumber: Int,
   endEpochBlockHash: Array[Byte],
   totalAmount: Long,
   outputs: Seq[MainchainBackwardTransferCertificateOutput])
  extends BytesSerializable
{
  override type M = MainchainBackwardTransferCertificate

  override def serializer: ScorexSerializer[MainchainBackwardTransferCertificate] = MainchainBackwardTransferCertificateSerializer

  def size: Int = certificateBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certificateBytes))

  override def bytes: Array[Byte] = {
    val outputBytes = MainchainBackwardTransferCertificate.outpustListSerializer.toBytes(outputs.asJava)
    Bytes.concat(
      Ints.toByteArray(certificateBytes.length),
      certificateBytes,
      Ints.toByteArray(version),
      Ints.toByteArray(sidechainId.length),
      sidechainId,
      Ints.toByteArray(epochNumber),
      Ints.toByteArray(endEpochBlockHash.length),
      endEpochBlockHash,
      Longs.toByteArray(totalAmount),
      Ints.toByteArray(outputBytes.length),
      outputBytes)
  }

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

    // We don't need to parse vbt_ccout array, because each vbt_ccout entry is an empty object

    val nounce: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val totalFee: Long = totalAmount - outputs.map(_.amount).sum
    val fee = totalFee / outputs.size

    val outputsWithFee = outputs.map(o => MainchainBackwardTransferCertificateOutput(o.outputBytes, o.pubKeyHash, o.amount, fee))

    new MainchainBackwardTransferCertificate(certificateBytes.slice(offset, currentOffset), version,
      sidechainId, epochNumber, endEpochBlockHash, totalAmount, outputsWithFee)

  }

  lazy val outpustListSerializer = new ListSerializer[MainchainBackwardTransferCertificateOutput](MainchainBackwardTransferCertificateOutputSerializer)

  def parseBytes(bytes: Array[Byte]): MainchainBackwardTransferCertificate = {
    var offset: Int = 0

    val certificateBytesSize = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset +=4

    val certificateBytes = bytes.slice(offset, offset + certificateBytesSize)
    offset += certificateBytesSize

    val version = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset +=4

    val sidechainIdSize = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset +=4

    val sidechainId = bytes.slice(offset, offset + sidechainIdSize)
    offset += sidechainIdSize

    val epochNumber = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset +=4

    val endEpochBlockHashSize = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset += 4

    val endEpochBlockHash = bytes.slice(offset, offset + endEpochBlockHashSize)
    offset += endEpochBlockHashSize

    val totalAmount = Longs.fromByteArray(bytes.slice(offset, offset + 8))
    offset += 8

    val outputsSize = Ints.fromByteArray(bytes.slice(offset, offset + 4))
    offset += 4

    val outputs = outpustListSerializer.parseBytes(bytes.slice(offset, offset + outputsSize))
    offset += outputsSize

    new MainchainBackwardTransferCertificate(certificateBytes, version, sidechainId, epochNumber,
      endEpochBlockHash, totalAmount, outputs.asScala)
  }
}

object MainchainBackwardTransferCertificateSerializer
  extends ScorexSerializer[MainchainBackwardTransferCertificate]
{
  override def serialize(certificate: MainchainBackwardTransferCertificate, w: Writer): Unit = {
    w.putBytes(certificate.bytes)
}

  override def parse(r: Reader): MainchainBackwardTransferCertificate = {
    MainchainBackwardTransferCertificate.parseBytes(r.getBytes(r.remaining))
  }

}

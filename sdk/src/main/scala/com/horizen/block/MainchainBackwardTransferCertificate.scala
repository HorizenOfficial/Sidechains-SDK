package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class MainchainBackwardTransferCertificate
  (certificateBytes: Array[Byte],
   version: Int,
   sidechainId: Array[Byte],
   epochNumber: Int,
   endEpochBlockHash: Array[Byte],
   previousEndEpochBlockHash: Array[Byte] = Array(),
   proof: Array[Byte] = Array(),
   totalAmount: Long,
   fee: Long,
   transactionOutputs: Seq[MainchainTransactionOutput],
   outputs: Seq[MainchainBackwardTransferCertificateOutput])
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

    val fee: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

    val transactionOutputCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += transactionOutputCount.size()

    var transactionOutputs: Seq[MainchainTransactionOutput] = Seq[MainchainTransactionOutput]()

    while(transactionOutputs.size < transactionOutputCount.value()) {
      val o: MainchainTransactionOutput = MainchainTransactionOutput.parse(certificateBytes, currentOffset)
      transactionOutputs = transactionOutputs :+ o
      currentOffset += o.size
    }

    val outputCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += outputCount.size()

    var outputs: Seq[MainchainBackwardTransferCertificateOutput] = Seq[MainchainBackwardTransferCertificateOutput]()

    while(outputs.size < outputCount.value()) {
      val o: MainchainBackwardTransferCertificateOutput = MainchainBackwardTransferCertificateOutput.parse(certificateBytes, currentOffset)
      outputs = outputs :+ o
      currentOffset += o.size
    }

    val nounce: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    new MainchainBackwardTransferCertificate(certificateBytes.slice(offset, currentOffset), version,
      sidechainId, epochNumber, endEpochBlockHash, Array(), Array(), totalAmount, fee, transactionOutputs, outputs)

  }
}

object MainchainBackwardTransferCertificateSerializer
  extends ScorexSerializer[MainchainBackwardTransferCertificate]
{
  override def serialize(certificate: MainchainBackwardTransferCertificate, w: Writer): Unit = {
    w.putBytes(certificate.certificateBytes)
}

  override def parse(r: Reader): MainchainBackwardTransferCertificate = {
    MainchainBackwardTransferCertificate.parse(r.getBytes(r.remaining), 0)
  }
}

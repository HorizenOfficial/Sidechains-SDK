package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class WithdrawalEpochCertificate
  (certificateBytes: Array[Byte],
   version: Int,
   sidechainId: Array[Byte],
   epochNumber: Int,
   endEpochBlockHash: Array[Byte],
   proof: Array[Byte] = Array(),
   quality: Long,
   totalAmount: Long,
   fee: Long,
   transactionOutputs: Seq[MainchainTransactionOutput],
   outputs: Seq[MainchainBackwardTransferCertificateOutput])
  extends BytesSerializable
{
  override type M = WithdrawalEpochCertificate

  override def serializer: ScorexSerializer[WithdrawalEpochCertificate] = MainchainBackwardTransferCertificateSerializer

  def size: Int = certificateBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certificateBytes))
}

object WithdrawalEpochCertificate {
  def parse(certificateBytes: Array[Byte], offset: Int) : WithdrawalEpochCertificate = {

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val epochNumber: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val endEpochBlockHash: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val quality: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

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

    new WithdrawalEpochCertificate(certificateBytes.slice(offset, currentOffset), version,
      sidechainId, epochNumber, endEpochBlockHash, Array(), totalAmount, fee, quality, transactionOutputs, outputs)

  }
}

object MainchainBackwardTransferCertificateSerializer
  extends ScorexSerializer[WithdrawalEpochCertificate]
{
  override def serialize(certificate: WithdrawalEpochCertificate, w: Writer): Unit = {
    w.putBytes(certificate.certificateBytes)
}

  override def parse(r: Reader): WithdrawalEpochCertificate = {
    WithdrawalEpochCertificate.parse(r.getBytes(r.remaining), 0)
  }
}

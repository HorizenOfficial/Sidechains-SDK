package com.horizen.block

import com.horizen.utils.{BytesUtils, ListSerializer, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

class MainchainBTCertificateOutput
  (val pubKeyHash: Array[Byte],
   val amount: Long)
  extends BytesSerializable
{
  override type M = MainchainBTCertificateOutput

  override def serializer: ScorexSerializer[MainchainBTCertificateOutput] = MainchainBTCertificateOutputSerializer

  var fee: Long = 0

  def originalAmount: Long = {
    amount + fee
  }
}

object MainchainBTCertificateOutput {
  def parse(outputBytes: Array[Byte], offset: Int): MainchainBTCertificateOutput = {

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(outputBytes, currentOffset)
    currentOffset += 8

    currentOffset += 4

    val pubKeyHash: Array[Byte] = BytesUtils.reverseBytes(outputBytes.slice(currentOffset, currentOffset + 32))

    new MainchainBTCertificateOutput(pubKeyHash, amount)
  }
}

object MainchainBTCertificateOutputSerializer
  extends ScorexSerializer[MainchainBTCertificateOutput]
{
  override def serialize(output: MainchainBTCertificateOutput, w: Writer): Unit = {
    w.putLong(output.amount)
    w.putLong(output.fee)
    w.putInt(output.pubKeyHash.length)
    w.putBytes(output.pubKeyHash)
  }

  override def parse(r: Reader): MainchainBTCertificateOutput = {
    val amount = r.getLong()
    val fee = r.getLong()
    val keySize = r.getInt()
    val pubKeyHash = r.getBytes(keySize)
    val output = new MainchainBTCertificateOutput(pubKeyHash, amount)
    output.fee = fee
    output
  }
}

class MainchainBackwardTransferCertificate
  (val certificateBytes: Array[Byte],
   val version: Int,
   val sidechainId: Array[Byte],
   val epochNumber: Int,
   val endEpochBlockHash: Array[Byte],
   val totalAmount: Long,
   val outputs: Seq[MainchainBTCertificateOutput])
  extends BytesSerializable
{
  override type M = MainchainBackwardTransferCertificate

  override def serializer: ScorexSerializer[MainchainBackwardTransferCertificate] = MainchainBackwardTransferCertificateSerializer

  def size: Int = certificateBytes.length

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

    val outputCount: VarInt = BytesUtils.getVarInt(certificateBytes, offset)
    currentOffset += outputCount.size()

    var outputs: Seq[MainchainBTCertificateOutput] = Seq[MainchainBTCertificateOutput]()

    while(offset < certificateBytes.length) {
      val o: MainchainBTCertificateOutput = MainchainBTCertificateOutput.parse(certificateBytes, offset)
      outputs = outputs :+ o
      currentOffset += 60
    }

    val totalFee: Long = totalAmount - outputs.map(_.amount).sum
    val fee = totalFee / outputs.size

    for(o <- outputs)
      o.fee = fee

    new MainchainBackwardTransferCertificate(certificateBytes, version, sidechainId, epochNumber, endEpochBlockHash, totalAmount, outputs)

  }
}

object MainchainBackwardTransferCertificateSerializer
  extends ScorexSerializer[MainchainBackwardTransferCertificate]
{

  private val outputsSerializer = new ListSerializer[MainchainBTCertificateOutput](MainchainBTCertificateOutputSerializer)

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

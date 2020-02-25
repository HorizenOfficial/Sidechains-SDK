package com.horizen.block

import com.horizen.utils.ListSerializer
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._

class MainchainBTCertificateOutput
  (val pubKeyHash: Array[Byte],
   val amount: Long,
   val originalAmount: Long)
  extends BytesSerializable
{
  override type M = MainchainBTCertificateOutput

  override def serializer: ScorexSerializer[MainchainBTCertificateOutput] = MainchainBTCertificateOutputSerializer
}

object MainchainBTCertificateOutputSerializer
  extends ScorexSerializer[MainchainBTCertificateOutput]
{
  override def serialize(output: MainchainBTCertificateOutput, w: Writer): Unit = {
    w.putLong(output.amount)
    w.putLong(output.originalAmount)
    w.putInt(output.pubKeyHash.length)
    w.putBytes(output.pubKeyHash)
  }

  override def parse(r: Reader): MainchainBTCertificateOutput = {
    val amount = r.getLong()
    val originalAmount = r.getLong()
    val keySize = r.getInt()
    val pubKeyHash = r.getBytes(keySize)
    new MainchainBTCertificateOutput(pubKeyHash, amount, originalAmount)
  }
}

class MainchainBackwardTransferCertificate
  (val certificateBytes: Array[Byte],
   val sidechainId: Array[Byte],
   val epochNumber: Int,
   val endEpochBlockHash: Array[Byte],
   val totalAmount: Long,
   val outputs: Seq[MainchainBTCertificateOutput])
  extends BytesSerializable
{
  override type M = MainchainBackwardTransferCertificate

  override def serializer: ScorexSerializer[MainchainBackwardTransferCertificate] = MainchainBackwardTransferCertificateSerializer
}

object MainchainBackwardTransferCertificate {
  def parse(certificateBytes: Array[Byte]) : MainchainBackwardTransferCertificate = ???
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
    MainchainBackwardTransferCertificate.parse(certificateBytes)
  }

}

package com.horizen.certificatesubmitter.network

import com.horizen.certificatesubmitter.CertificateSubmitter.CertificateSignatureInfo
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.proof.SchnorrSignatureSerializer
import scorex.core.network.message.{Message, MessageSpecV1}
import scorex.util.serialization.{Reader, Writer}

/**
 * Specify the unknown certificate signatures indexes
 * to reduce the response data size avoiding known data duplication.
 */
case class InvUnknownSignatures(indexes: Seq[Int])

object GetCertificateSignaturesSpec {
  val messageCode: Message.MessageCode = 44: Byte
  val messageName: String = "GetCertificateSignatures message"
}

/**
 * The `GetCertificateSignaturesSpec` message requests an `CertificateSignatures` message from the receiving node,
 * containing the unknown signatures to the current node.
 */
class GetCertificateSignaturesSpec(unknownSignaturesLimit: Int) extends MessageSpecV1[InvUnknownSignatures] {
  override val messageCode: Message.MessageCode = GetCertificateSignaturesSpec.messageCode

  override val messageName: String = GetCertificateSignaturesSpec.messageName

  override def serialize(inv: InvUnknownSignatures, w: Writer): Unit = {
    w.putUInt(inv.indexes.size)
    inv.indexes.foreach(idx => w.putInt(idx))
  }

  override def parse(r: Reader): InvUnknownSignatures = {
    val length = r.getUInt().toInt
    require(length <= unknownSignaturesLimit, s"Too many signature indexes requested. $length exceeds limit $unknownSignaturesLimit")
    val indexes = (0 until length).map(_ => r.getInt())
    InvUnknownSignatures(indexes)
  }
}

/**
 * Message signed by the list of the known signatures zipped with the public key indexes for the Threshold signature circuit.
 * We send the `messageToSign` to let the receiver to distinguish between different `messageToSign` (different epoch data)
 * and invalid data (signature or index) that is expected to react in other way.
 */
case class KnownSignatures(messageToSign: Array[Byte], signaturesInfo: Seq[CertificateSignatureInfo])

object CertificateSignaturesSpec {
  val messageCode: Message.MessageCode = 45: Byte
  val messageName: String = "CertificateSignatures message"
}

/**
 * The `CertificateSignaturesSpec` message is a reply to a `GetCertificateSignaturesSpec` message
 * and provides with the known signatures for the requested indexes.
 */
class CertificateSignaturesSpec(signaturesLimit: Int) extends MessageSpecV1[KnownSignatures] {
  private val proofSerializer = SchnorrSignatureSerializer.getSerializer

  override val messageCode: Message.MessageCode = CertificateSignaturesSpec.messageCode

  override val messageName: String = CertificateSignaturesSpec.messageName

  override def serialize(inv: KnownSignatures, w: Writer): Unit = {
    require(inv.messageToSign.length == FieldElementUtils.fieldElementLength(), "messageToSign has invalid length")
    require(inv.signaturesInfo.nonEmpty, "empty signaturesInfo list")
    require(inv.signaturesInfo.size <= signaturesLimit, s"more signatures info entries than max allowed $signaturesLimit in a message")

    w.putBytes(inv.messageToSign)

    w.putUInt(inv.signaturesInfo.size)
    inv.signaturesInfo.foreach(info => {
      w.putInt(info.pubKeyIndex)
      proofSerializer.serialize(info.signature, w)
    })

  }

  override def parse(r: Reader): KnownSignatures = {
    val messageToSign = r.getBytes(FieldElementUtils.fieldElementLength())

    val length = r.getUInt().toInt
    require(length <= signaturesLimit, s"Too many signatures info entries. $length exceeds limit $signaturesLimit")
    val signaturesInfo = (0 until length).map(_ => {
      val pubKeyIndex = r.getInt()
      val signature = proofSerializer.parse(r)
      CertificateSignatureInfo(pubKeyIndex, signature)
    })

    KnownSignatures(messageToSign, signaturesInfo)
  }
}

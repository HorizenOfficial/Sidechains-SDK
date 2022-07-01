package com.horizen.account.state

import com.horizen.account.utils.ZenWeiConverter
import com.horizen.proposition.{MCPublicKeyHashProposition, MCPublicKeyHashPropositionSerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class WithdrawalRequest(proposition: MCPublicKeyHashProposition, value: java.math.BigInteger) extends BytesSerializable {
  override type M = WithdrawalRequest

  override def serializer: ScorexSerializer[WithdrawalRequest] = WithdrawalRequestSerializer

  val valueInZennies: Long = ZenWeiConverter.convertWeiToZennies(value)
}

object WithdrawalRequestSerializer extends ScorexSerializer[WithdrawalRequest] {
  override def serialize(obj: WithdrawalRequest, writer: Writer): Unit = {
    MCPublicKeyHashPropositionSerializer.getSerializer.serialize(obj.proposition, writer)
    val byteArray = obj.value.toByteArray
    writer.putUInt(byteArray.length)
    writer.putBytes(byteArray)
  }

  override def parse(reader: Reader): WithdrawalRequest = {
    val proposition = MCPublicKeyHashPropositionSerializer.getSerializer.parse(reader)
    val valueByteArrayLength = reader.getUInt().toInt
    val value = new java.math.BigInteger(reader.getBytes(valueByteArrayLength))
    WithdrawalRequest(proposition, value)

  }
}
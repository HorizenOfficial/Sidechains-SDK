package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.ABIEncodable
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.json.Views
import io.horizen.proposition.{MCPublicKeyHashProposition, MCPublicKeyHashPropositionSerializer}
import org.web3j.abi.datatypes.StaticStruct
import org.web3j.abi.datatypes.generated.{Bytes20, Uint256}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class WithdrawalRequest(proposition: MCPublicKeyHashProposition, value: java.math.BigInteger) extends BytesSerializable with ABIEncodable[StaticStruct] {
  override type M = WithdrawalRequest

  override def serializer: SparkzSerializer[WithdrawalRequest] = WithdrawalRequestSerializer

  val valueInZennies: Long = ZenWeiConverter.convertWeiToZennies(value)

  private[horizen] def asABIType(): StaticStruct = {
    new StaticStruct(new Bytes20(proposition.bytes), new Uint256(value))
  }

}

object WithdrawalRequestSerializer extends SparkzSerializer[WithdrawalRequest] {
  override def serialize(obj: WithdrawalRequest, writer: Writer): Unit = {
    MCPublicKeyHashPropositionSerializer.getSerializer.serialize(obj.proposition, writer)
    val byteArray = obj.value.toByteArray
    writer.putUInt(byteArray.length)
    writer.putBytes(byteArray)
  }

  override def parse(reader: Reader): WithdrawalRequest = {
    val proposition = MCPublicKeyHashPropositionSerializer.getSerializer.parse(reader)
    val valueByteArrayLength = reader.getUInt().toInt
    val value = new BigInteger(reader.getBytes(valueByteArrayLength))
    WithdrawalRequest(proposition, value)

  }
}
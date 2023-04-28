package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIEncodable
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes20, Bytes32, Bytes4, Uint32}
import org.web3j.abi.datatypes.{DynamicBytes, StaticStruct}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class AccountCrossChainMessage
(
  messageType: Int,
  sender: Array[Byte], //we keep it generic because the format is dependant on the sidechain type
  receiverSidechain: Array[Byte],
  receiver: Array[Byte], //we keep it generic because  the format is dependant on the sidechain type
  payload: Array[Byte]
) extends BytesSerializable with ABIEncodable[StaticStruct] {

  override type M = AccountCrossChainMessage

  override def serializer: SparkzSerializer[AccountCrossChainMessage] = AccountCrossChainMessageSerializer

  //todo: payload must be dynamic bytes
  private[horizen] def asABIType(): StaticStruct = {
    new StaticStruct(
      new Uint32(messageType),
      new Bytes20(sender),
      new Bytes32(receiverSidechain),
      new Bytes20(receiver),
      new Bytes4(payload)
    )
  }
}

object AccountCrossChainMessageSerializer extends SparkzSerializer[AccountCrossChainMessage] {
  override def serialize(obj: AccountCrossChainMessage, writer: Writer): Unit = {
    writer.putUInt(obj.messageType)
    writeBytes(writer, obj.sender)
    writeBytes(writer, obj.receiverSidechain)
    writeBytes(writer, obj.receiver)
    writeBytes(writer, obj.payload)
  }

  override def parse(reader: Reader): AccountCrossChainMessage = {
    val messageType = reader.getUInt().toInt
    val sender = parseNextBytes(reader)
    val receiverSidechain = parseNextBytes(reader)
    val receiver = parseNextBytes(reader)
    val payload = parseNextBytes(reader)
    AccountCrossChainMessage(messageType, sender, receiverSidechain, receiver, payload)
  }

  private def writeBytes(writer: Writer, value: Array[Byte]): Unit = {
    writer.putUInt(value.length)
    writer.putBytes(value)
  }

  private def parseNextBytes(reader: Reader): Array[Byte] = {
    val valueByteArrayLength = reader.getUInt().toInt
    reader.getBytes(valueByteArrayLength)
  }
}
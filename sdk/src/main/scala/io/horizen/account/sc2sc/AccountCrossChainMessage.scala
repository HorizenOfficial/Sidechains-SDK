package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIEncodable
import io.horizen.account.proposition.AddressProposition
import io.horizen.sc2sc.CrossChainMessageSemanticValidator
import org.web3j.abi.datatypes.StaticStruct
import org.web3j.abi.datatypes.generated.{Bytes20, Bytes32, Uint32}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class AccountCrossChainMessage
(
  messageType: Int,
  sender: Array[Byte], //we keep it generic because the format is dependant on the sidechain type
  receiverSidechain: Array[Byte],
  receiver: Array[Byte], //we keep it generic because  the format is dependant on the sidechain type
  payloadHash: Array[Byte]
) extends BytesSerializable with ABIEncodable[StaticStruct] {

  override type M = AccountCrossChainMessage

  AccountCrossChainMessageValidator.ccMsgValidator.validateMessage(this)

  override def serializer: SparkzSerializer[AccountCrossChainMessage] = AccountCrossChainMessageSerializer

  private[horizen] def asABIType(): StaticStruct = {
    val senderAddressABI = if (sender.length == AddressProposition.LENGTH) new Bytes20(sender)
                           else new Bytes32(sender)
    val receiverAddressABI = if (receiver.length == AddressProposition.LENGTH) new Bytes20(receiver)
                             else new Bytes32(receiver)
    new StaticStruct(
      new Uint32(messageType),
      senderAddressABI,
      new Bytes32(receiverSidechain),
      receiverAddressABI,
      new Bytes32(payloadHash)
    )
  }
}

object AccountCrossChainMessageSerializer extends SparkzSerializer[AccountCrossChainMessage] {
  override def serialize(obj: AccountCrossChainMessage, writer: Writer): Unit = {
    writer.putUInt(obj.messageType)
    writeBytes(writer, obj.sender)
    writeBytes(writer, obj.receiverSidechain)
    writeBytes(writer, obj.receiver)
    writeBytes(writer, obj.payloadHash)
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

object AccountCrossChainMessageValidator {
  val ccMsgValidator = new CrossChainMessageSemanticValidator()
}
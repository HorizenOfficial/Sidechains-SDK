package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIEncodable
import org.web3j.abi.datatypes.generated.{Bytes20, Bytes32, Bytes4, Uint32}
import org.web3j.abi.datatypes.{DynamicBytes, StaticStruct}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class AccountCrossChainRedeemMessage
(
  messageType: Int,
  sender: Array[Byte], //we keep it generic because the format is dependant on the sidechain type
  receiverSidechain: Array[Byte],
  receiver: Array[Byte], //we keep it generic because  the format is dependant on the sidechain type
  payload: Array[Byte],
  certificateDataHash: Array[Byte],
  nextCertificateDataHash: Array[Byte],
  scCommitmentTreeRoot: Array[Byte],
  nextScCommitmentTreeRoot: Array[Byte],
  proof: Array[Byte]
) extends BytesSerializable with ABIEncodable[StaticStruct] {
  override type M = AccountCrossChainRedeemMessage

  override def serializer: SparkzSerializer[AccountCrossChainRedeemMessage] = AccountCrossChainRedeemMessageSerializer

  override def asABIType(): StaticStruct =
    new StaticStruct(
      new Uint32(messageType),
      new Bytes20(sender),
      new Bytes32(receiverSidechain),
      new Bytes20(receiver),
      new Bytes4(payload),
      new Bytes32(certificateDataHash),
      new Bytes32(nextCertificateDataHash),
      new Bytes32(scCommitmentTreeRoot),
      new Bytes32(nextScCommitmentTreeRoot),
      new DynamicBytes(proof)
    )
}

object AccountCrossChainRedeemMessageSerializer extends SparkzSerializer[AccountCrossChainRedeemMessage] {
  override def serialize(redeemMsg: AccountCrossChainRedeemMessage, w: Writer): Unit = {
    w.putUInt(redeemMsg.messageType)
    writeBytes(w, redeemMsg.sender)
    writeBytes(w, redeemMsg.receiverSidechain)
    writeBytes(w, redeemMsg.receiver)
    writeBytes(w, redeemMsg.payload)
    writeBytes(w, redeemMsg.certificateDataHash)
    writeBytes(w, redeemMsg.nextCertificateDataHash)
    writeBytes(w, redeemMsg.scCommitmentTreeRoot)
    writeBytes(w, redeemMsg.nextScCommitmentTreeRoot)
    writeBytes(w, redeemMsg.proof)
  }

  override def parse(r: Reader): AccountCrossChainRedeemMessage = {
    val messageType = r.getUInt().toInt
    val sender = parseNextBytes(r)
    val receiverSidechain = parseNextBytes(r)
    val receiver = parseNextBytes(r)
    val payload = parseNextBytes(r)
    AccountCrossChainMessage(messageType, sender, receiverSidechain, receiver, payload)
    val certificateDataHash = parseNextBytes(r)
    val nextCertificateDataHash = parseNextBytes(r)
    val scCommitmentTreeRoot = parseNextBytes(r)
    val nextScCommitmentTreeRoot = parseNextBytes(r)
    val proof = parseNextBytes(r)

    AccountCrossChainRedeemMessage(
      messageType, sender, receiverSidechain, receiver, payload,
      certificateDataHash,
      nextCertificateDataHash,
      scCommitmentTreeRoot,
      nextScCommitmentTreeRoot,
      proof
    )
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
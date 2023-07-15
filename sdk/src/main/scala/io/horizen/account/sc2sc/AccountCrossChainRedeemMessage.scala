package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIEncodable
import io.horizen.account.proposition.AddressProposition
import io.horizen.sc2sc.CrossChainRedeemMessageSemanticValidator
import io.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.generated.{Bytes20, Bytes32, Uint32}
import org.web3j.abi.datatypes.{DynamicStruct, Utf8String}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class AccountCrossChainRedeemMessage
(
  messageType: Int,
  senderSidechain: Array[Byte],
  sender: Array[Byte], //we keep it generic because the format is dependant on the sidechain type
  receiverSidechain: Array[Byte],
  receiver: Array[Byte], //we keep it generic because  the format is dependant on the sidechain type
  payload: Array[Byte],
  certificateDataHash: Array[Byte],
  nextCertificateDataHash: Array[Byte],
  scCommitmentTreeRoot: Array[Byte],
  nextScCommitmentTreeRoot: Array[Byte],
  proof: Array[Byte]
) extends BytesSerializable with ABIEncodable[DynamicStruct] {
  override type M = AccountCrossChainRedeemMessage

  AccountCrossChainRedeemMessageSemanticValidator.ccMsgValidator.validateMessage(this)
  override def serializer: SparkzSerializer[AccountCrossChainRedeemMessage] = AccountCrossChainRedeemMessageSerializer

  override def asABIType(): DynamicStruct = {
    val senderAddressABI = if (sender.length == AddressProposition.LENGTH) new Bytes20(sender)
    else new Bytes32(sender)
    val receiverAddressABI = if (receiver.length == AddressProposition.LENGTH) new Bytes20(receiver)
    else new Bytes32(receiver)

    new DynamicStruct(
      new Uint32(messageType),
      new Bytes32(senderSidechain),
      senderAddressABI,
      new Bytes32(receiverSidechain),
      receiverAddressABI,
      new Utf8String(payload.map(_.toChar).mkString),
      new Bytes32(certificateDataHash),
      new Bytes32(nextCertificateDataHash),
      new Bytes32(scCommitmentTreeRoot),
      new Bytes32(nextScCommitmentTreeRoot),
      new Utf8String(BytesUtils.toHexString(proof)),
    )
  }

  override def hashCode(): Int = super.hashCode()

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: AccountCrossChainRedeemMessage =>
        messageType == that.messageType && senderSidechain.sameElements(that.senderSidechain) && sender.sameElements(that.sender) &&
          receiverSidechain.sameElements(that.receiverSidechain) && receiver.sameElements(that.receiver) &&
          payload.sameElements(that.payload) && certificateDataHash.sameElements(that.certificateDataHash) &&
          nextCertificateDataHash.sameElements(that.nextCertificateDataHash) && scCommitmentTreeRoot.sameElements(scCommitmentTreeRoot) &&
          nextScCommitmentTreeRoot.sameElements(that.nextScCommitmentTreeRoot) && proof.sameElements(that.proof)

      case _ => false
    }
  }
}

object AccountCrossChainRedeemMessageSerializer extends SparkzSerializer[AccountCrossChainRedeemMessage] {
  override def serialize(redeemMsg: AccountCrossChainRedeemMessage, w: Writer): Unit = {
    w.putUInt(redeemMsg.messageType)
    writeBytes(w, redeemMsg.senderSidechain)
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
    val senderSidechain = parseNextBytes(r)
    val sender = parseNextBytes(r)
    val receiverSidechain = parseNextBytes(r)
    val receiver = parseNextBytes(r)
    val payload = parseNextBytes(r)

    val certificateDataHash = parseNextBytes(r)
    val nextCertificateDataHash = parseNextBytes(r)
    val scCommitmentTreeRoot = parseNextBytes(r)
    val nextScCommitmentTreeRoot = parseNextBytes(r)
    val proof = parseNextBytes(r)

    AccountCrossChainRedeemMessage(
      messageType, senderSidechain, sender, receiverSidechain, receiver, payload,
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

object AccountCrossChainRedeemMessageSemanticValidator {
  val ccMsgValidator = new CrossChainRedeemMessageSemanticValidator()
}
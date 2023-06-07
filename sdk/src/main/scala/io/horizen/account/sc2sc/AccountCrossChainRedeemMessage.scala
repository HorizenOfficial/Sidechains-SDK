package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIEncodable
import io.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.{DynamicArray, DynamicStruct, StaticStruct, Utf8String}
import org.web3j.abi.datatypes.generated.{Bytes20, Bytes32, Bytes4, Uint32}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.util

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
) extends BytesSerializable with ABIEncodable[DynamicStruct] {
  override type M = AccountCrossChainRedeemMessage

  override def serializer: SparkzSerializer[AccountCrossChainRedeemMessage] = AccountCrossChainRedeemMessageSerializer

  private def chunkProofIn32Bytes(bytes: Array[Byte]): java.util.List[Bytes32] = {
    val result = new util.ArrayList[Bytes32]()
    val chunkSize = 32
    var start = 0
    while (start < bytes.length) {
      val end = Math.min(bytes.length, start + chunkSize)
      val toBeInserted = Array.fill[Byte](32)(0)
      val chunkCopy = util.Arrays.copyOfRange(bytes, start, end)

      var index = 0
      while (index < chunkCopy.length) {
        toBeInserted(index) = chunkCopy(index)
        index += 1
      }

      result.add(new Bytes32(toBeInserted))
      start += chunkSize
    }
    result
  }

  override def asABIType(): DynamicStruct = {
    new DynamicStruct(
      new Uint32(messageType),
      new Bytes20(sender),
      new Bytes32(receiverSidechain),
      new Bytes20(receiver),
      new Bytes4(payload),
      new Bytes32(certificateDataHash),
      new Bytes32(nextCertificateDataHash),
      new Bytes32(scCommitmentTreeRoot),
      new Bytes32(nextScCommitmentTreeRoot),
      new Utf8String(BytesUtils.toHexString(proof)),
    )
  }
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
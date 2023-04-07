package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIEncodable
import org.web3j.abi.datatypes.{DynamicBytes, StaticStruct}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class AccountCrossChainRedeemMessage
(
  accountCrossChainMessage: AccountCrossChainMessage,
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
      new DynamicBytes(accountCrossChainMessage.bytes),
      new DynamicBytes(certificateDataHash),
      new DynamicBytes(nextCertificateDataHash),
      new DynamicBytes(scCommitmentTreeRoot),
      new DynamicBytes(nextScCommitmentTreeRoot),
      new DynamicBytes(proof)
    )
}

object AccountCrossChainRedeemMessageSerializer extends SparkzSerializer[AccountCrossChainRedeemMessage] {
  override def serialize(redeemMsg: AccountCrossChainRedeemMessage, w: Writer): Unit = {
    writeBytes(w, redeemMsg.accountCrossChainMessage.bytes)
    writeBytes(w, redeemMsg.certificateDataHash)
    writeBytes(w, redeemMsg.nextCertificateDataHash)
    writeBytes(w, redeemMsg.scCommitmentTreeRoot)
    writeBytes(w, redeemMsg.nextScCommitmentTreeRoot)
    writeBytes(w, redeemMsg.proof)
  }

  override def parse(r: Reader): AccountCrossChainRedeemMessage = {
    val crossChainMessage = AccountCrossChainMessageSerializer.parse(r)
    val certificateDataHash = parseNextBytes(r)
    val nextCertificateDataHash = parseNextBytes(r)
    val scCommitmentTreeRoot = parseNextBytes(r)
    val nextScCommitmentTreeRoot = parseNextBytes(r)
    val proof = parseNextBytes(r)

    AccountCrossChainRedeemMessage(
      crossChainMessage,
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
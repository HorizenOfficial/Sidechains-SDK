package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.Bytes
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.serialization.{ReverseBytesSerializer, Views}
import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
import com.horizen.librustsidechains.{Utils => ScCryptoUtils}

import scala.util.Try

case class FieldElementCertificateField(rawData: Array[Byte]) {
  lazy val fieldElementBytes: Array[Byte] = {
    Bytes.concat(new Array[Byte](FieldElementUtils.fieldElementLength() - rawData.length), rawData)
  }
}
case class BitVectorCertificateField(rawData: Array[Byte]) {
  lazy val merkleRootBytes: Array[Byte] = {
    ScCryptoUtils.compressedBitvectorMerkleRoot(rawData)
  }

  def tryMerkleRootBytesWithCheck(uncompressedSize: Int): Try[Array[Byte]] = Try {
    ScCryptoUtils.compressedBitvectorMerkleRoot(rawData, uncompressedSize)
  }
}

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("certificateBytes", "transactionInputs", "transactionOutputs"))
case class WithdrawalEpochCertificate
  (certificateBytes: Array[Byte],
   version: Int,
   @JsonSerialize(using = classOf[ReverseBytesSerializer]) sidechainId: Array[Byte],
   epochNumber: Int,
   quality: Long,
   proof: Array[Byte],
   fieldElementCertificateFields: Seq[FieldElementCertificateField],
   bitVectorCertificateFields: Seq[BitVectorCertificateField],
   endCumulativeScTxCommitmentTreeRoot: Array[Byte],
   btrFee: Long,
   ftMinAmount: Long,
   transactionInputs: Seq[MainchainTransactionInput],
   transactionOutputs: Seq[MainchainTransactionOutput],
   backwardTransferOutputs: Seq[MainchainBackwardTransferCertificateOutput])
  extends BytesSerializable
{
  override type M = WithdrawalEpochCertificate

  override def serializer: ScorexSerializer[WithdrawalEpochCertificate] = WithdrawalEpochCertificateSerializer

  def size: Int = certificateBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certificateBytes))

  lazy val customFieldsOpt: Option[Array[Array[Byte]]] = {
    if(fieldElementCertificateFields.isEmpty && bitVectorCertificateFields.isEmpty)
      None
    else {
      val customFields: Seq[Array[Byte]] = fieldElementCertificateFields.map(_.fieldElementBytes) ++ bitVectorCertificateFields.map(_.merkleRootBytes)
      Some(customFields.toArray)
    }
  }
}

object WithdrawalEpochCertificate {
  def parse(certificateBytes: Array[Byte], offset: Int) : WithdrawalEpochCertificate = {

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val sidechainId: Array[Byte] = certificateBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val epochNumber: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val quality: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

    val endCumulativeScTxCommitmentTreeRootSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += endCumulativeScTxCommitmentTreeRootSize.size()
    if(endCumulativeScTxCommitmentTreeRootSize.value() != FieldElementUtils.fieldElementLength())
      throw new IllegalArgumentException(s"Input data corrupted: endCumulativeScTxCommitmentTreeRoot size ${endCumulativeScTxCommitmentTreeRootSize.value()} " +
        s"is expected to be FieldElement size ${FieldElementUtils.fieldElementLength()}")

    val endCumulativeScTxCommitmentTreeRoot: Array[Byte] = certificateBytes.slice(
      currentOffset, currentOffset + endCumulativeScTxCommitmentTreeRootSize.value().intValue())
    currentOffset += endCumulativeScTxCommitmentTreeRootSize.value().intValue()

    val scProofSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += scProofSize.size()
    val scProof: Array[Byte] = certificateBytes.slice(currentOffset, currentOffset + scProofSize.value().intValue())
    currentOffset += scProofSize.value().intValue()

    val fieldElementCertificateFieldsLength: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += fieldElementCertificateFieldsLength.size()

    val fieldElementCertificateFields: Seq[FieldElementCertificateField] =
      (1 to fieldElementCertificateFieldsLength.value().intValue()).map ( _ => {
        val certFieldSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
        currentOffset += certFieldSize.size()
        val rawData: Array[Byte] = certificateBytes.slice(currentOffset, currentOffset + certFieldSize.value().intValue())
        currentOffset += certFieldSize.value().intValue()

        FieldElementCertificateField(rawData)
      })

    val bitVectorCertificateFieldsLength: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += bitVectorCertificateFieldsLength.size()

    val bitVectorCertificateFields: Seq[BitVectorCertificateField] =
      (1 to bitVectorCertificateFieldsLength.value().intValue()).map ( _ => {
        val certBitVectorSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
        currentOffset += certBitVectorSize.size()
        val rawData: Array[Byte] = certificateBytes.slice(currentOffset, currentOffset + certBitVectorSize.value().intValue())
        currentOffset += certBitVectorSize.value().intValue()
        BitVectorCertificateField(rawData)
      })

    val ftMinAmount: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

    val btrFee: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

    val transactionInputCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += transactionInputCount.size()

    var transactionInputs: Seq[MainchainTransactionInput] = Seq[MainchainTransactionInput]()

    while(transactionInputs.size < transactionInputCount.value()) {
      val input: MainchainTransactionInput = MainchainTransactionInput.parse(certificateBytes, currentOffset)
      transactionInputs = transactionInputs :+ input
      currentOffset += input.size
    }

    val transactionOutputCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += transactionOutputCount.size()

    var transactionOutputs: Seq[MainchainTransactionOutput] = Seq[MainchainTransactionOutput]()

    while(transactionOutputs.size < transactionOutputCount.value()) {
      val o: MainchainTransactionOutput = MainchainTransactionOutput.parse(certificateBytes, currentOffset)
      transactionOutputs = transactionOutputs :+ o
      currentOffset += o.size
    }

    val backwardTransferOutputsCount: VarInt = BytesUtils.getVarInt(certificateBytes, currentOffset)
    currentOffset += backwardTransferOutputsCount.size()

    var backwardTransferOutputs: Seq[MainchainBackwardTransferCertificateOutput] = Seq[MainchainBackwardTransferCertificateOutput]()

    while(backwardTransferOutputs.size < backwardTransferOutputsCount.value()) {
      val o: MainchainBackwardTransferCertificateOutput = MainchainBackwardTransferCertificateOutput.parse(certificateBytes, currentOffset)
      backwardTransferOutputs = backwardTransferOutputs :+ o
      currentOffset += o.size
    }

    new WithdrawalEpochCertificate(
      certificateBytes.slice(offset, currentOffset),
      version,
      sidechainId,
      epochNumber,
      quality,
      scProof,
      fieldElementCertificateFields,
      bitVectorCertificateFields,
      endCumulativeScTxCommitmentTreeRoot,
      btrFee,
      ftMinAmount,
      transactionInputs,
      transactionOutputs,
      backwardTransferOutputs)
  }
}

object WithdrawalEpochCertificateSerializer
  extends ScorexSerializer[WithdrawalEpochCertificate]
{
  override def serialize(certificate: WithdrawalEpochCertificate, w: Writer): Unit = {
    w.putBytes(certificate.certificateBytes)
}

  override def parse(r: Reader): WithdrawalEpochCertificate = {
    WithdrawalEpochCertificate.parse(r.getBytes(r.remaining), 0)
  }
}

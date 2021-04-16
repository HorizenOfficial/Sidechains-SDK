package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.serialization.Views
import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class FieldElementCertificateField(rawData: Array[Byte])
case class BitVectorCertificateField(rawData: Array[Byte])

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("certificateBytes", "transactionInputs", "transactionOutputs"))
case class WithdrawalEpochCertificate
  (certificateBytes: Array[Byte],
   version: Int,
   sidechainId: Array[Byte],
   epochNumber: Int,
   quality: Long,
   endEpochBlockHash: Array[Byte],
   proof: Array[Byte],
   fieldElementCertificateFields: Seq[FieldElementCertificateField],
   bitVectorCertificateFields: Seq[BitVectorCertificateField],
   transactionInputs: Seq[MainchainTransactionInput],
   transactionOutputs: Seq[MainchainTransactionOutput],
   backwardTransferOutputs: Seq[MainchainBackwardTransferCertificateOutput])
  extends BytesSerializable
{
  override type M = WithdrawalEpochCertificate

  override def serializer: ScorexSerializer[WithdrawalEpochCertificate] = WithdrawalEpochCertificateSerializer

  def size: Int = certificateBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certificateBytes))
}

object WithdrawalEpochCertificate {
  def parse(certificateBytes: Array[Byte], offset: Int) : WithdrawalEpochCertificate = {

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val epochNumber: Int = BytesUtils.getReversedInt(certificateBytes, currentOffset)
    currentOffset += 4

    val quality: Long = BytesUtils.getReversedLong(certificateBytes, currentOffset)
    currentOffset += 8

    val endEpochBlockHash: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val scProofSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += scProofSize.size()
    if(scProofSize.value() != CryptoLibProvider.sigProofThresholdCircuitFunctions.proofSizeLength())
      throw new IllegalArgumentException(s"Input data corrupted: scProof size ${scProofSize.value()} " +
        s"is expected to be ScProof size ${CryptoLibProvider.sigProofThresholdCircuitFunctions.proofSizeLength()}")

    val scProof: Array[Byte] = certificateBytes.slice(currentOffset, currentOffset + scProofSize.value().intValue())
    currentOffset += scProofSize.value().intValue()

    val fieldElementCertificateFieldsLength: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += fieldElementCertificateFieldsLength.size()

    val fieldElementCertificateFields: Seq[FieldElementCertificateField] =
      (1 to fieldElementCertificateFieldsLength.size()).map ( _ => {
        val certFieldSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
        currentOffset += certFieldSize.size()
        val rawData: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + certFieldSize.value().intValue()))
        currentOffset += certFieldSize.value().intValue()

        FieldElementCertificateField(rawData)
      })

    val bitVectorCertificateFieldsLength: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
    currentOffset += bitVectorCertificateFieldsLength.size()

    val bitVectorCertificateFields: Seq[BitVectorCertificateField] =
      (1 to bitVectorCertificateFieldsLength.size()).map ( _ => {
        val vertBitVectorSize: VarInt = BytesUtils.getReversedVarInt(certificateBytes, currentOffset)
        currentOffset += vertBitVectorSize.size()
        val rawData: Array[Byte] = BytesUtils.reverseBytes(certificateBytes.slice(currentOffset, currentOffset + vertBitVectorSize.value().intValue()))
        currentOffset += vertBitVectorSize.value().intValue()
        BitVectorCertificateField(rawData)
      })

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
      endEpochBlockHash,
      scProof,
      fieldElementCertificateFields,
      bitVectorCertificateFields,
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

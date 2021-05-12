package com.horizen.block

import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.librustsidechains.FieldElement
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

case class MainchainTxBwtRequestCrosschainOutput(bwtRequestOutputBytes: Array[Byte],
                                                 override val sidechainId: Array[Byte], // uint256
                                                 scRequestData: Array[Array[Byte]],     // ScFieldElement[]
                                                 mcDestinationAddress: Array[Byte],     // uint160
                                                 scFee: Long,                           // CAmount (int64_t)
                                                 scProof: Array[Byte]                   // ScProof
                                                ) extends MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(bwtRequestOutputBytes))
}

object MainchainTxBwtRequestCrosschainOutput {
  val BWT_REQUEST_OUTPUT_SIZE: Int = 32 + FieldElement.FIELD_ELEMENT_LENGTH + 20 + 8 + CryptoLibProvider.sigProofThresholdCircuitFunctions.proofSizeLength()

  def create(bwtRequestOutputBytes: Array[Byte], offset: Int): Try[MainchainTxBwtRequestCrosschainOutput] = Try {
    if(offset < 0 || bwtRequestOutputBytes.length - offset < BWT_REQUEST_OUTPUT_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(bwtRequestOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    // TODO: check fields order serialization in MC
    val scRequestDataSize: VarInt = BytesUtils.getReversedVarInt(bwtRequestOutputBytes, currentOffset)
    currentOffset += scRequestDataSize.size()

    val scRequestDataSeq: Seq[Array[Byte]] = (1 to scRequestDataSize.value().intValue()).map(idx => {
      val dataSize = BytesUtils.getReversedVarInt(bwtRequestOutputBytes, currentOffset)
      currentOffset += scRequestDataSize.size()

      if(dataSize.value() != FieldElement.FIELD_ELEMENT_LENGTH)
        throw new IllegalArgumentException(s"Input data corrupted: scRequestData[$idx] size ${dataSize.value()} " +
          s"is expected to be FieldElement size ${FieldElement.FIELD_ELEMENT_LENGTH}")

      val scRequestData: Array[Byte] = BytesUtils.reverseBytes(bwtRequestOutputBytes.slice(currentOffset, currentOffset + scRequestDataSize.value().intValue()))
      currentOffset += scRequestDataSize.value().intValue()

      scRequestData
    })

    val mcDestinationAddress: Array[Byte] = BytesUtils.reverseBytes(bwtRequestOutputBytes.slice(currentOffset, currentOffset + 20))
    currentOffset += 20

    val scFee: Long = BytesUtils.getReversedLong(bwtRequestOutputBytes, currentOffset)
    currentOffset += 8

    // TODO: Proof will have variable size
    val scProofSize: VarInt = BytesUtils.getReversedVarInt(bwtRequestOutputBytes, currentOffset)
    currentOffset += scProofSize.size()
    if(scProofSize.value() != CryptoLibProvider.sigProofThresholdCircuitFunctions.proofSizeLength())
      throw new IllegalArgumentException(s"Input data corrupted: scProof size ${scProofSize.value()} " +
        s"is expected to be ScProof size ${CryptoLibProvider.sigProofThresholdCircuitFunctions.proofSizeLength()}")

    val scProof: Array[Byte] = bwtRequestOutputBytes.slice(currentOffset, currentOffset + scProofSize.value().intValue())
    currentOffset += scProofSize.value().intValue()

    new MainchainTxBwtRequestCrosschainOutput(bwtRequestOutputBytes.slice(offset, currentOffset),
      sidechainId, scRequestDataSeq.toArray, mcDestinationAddress, scFee, scProof)
  }
}

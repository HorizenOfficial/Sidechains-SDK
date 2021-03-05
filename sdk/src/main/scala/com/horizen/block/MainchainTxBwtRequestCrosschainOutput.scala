package com.horizen.block

import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.librustsidechains.FieldElement
import com.horizen.utils.{BytesUtils, Utils}

import scala.util.Try

case class MainchainTxBwtRequestCrosschainOutput(bwtRequestOutputBytes: Array[Byte],
                                                 override val sidechainId: Array[Byte], // uint256
                                                 scRequestData: Array[Byte],            // ScFieldElement
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

    val scFieldElementSize: Int = FieldElement.FIELD_ELEMENT_LENGTH
    val scRequestData: Array[Byte] = BytesUtils.reverseBytes(bwtRequestOutputBytes.slice(currentOffset, currentOffset + scFieldElementSize))
    currentOffset += scFieldElementSize

    val mcDestinationAddress: Array[Byte] = BytesUtils.reverseBytes(bwtRequestOutputBytes.slice(currentOffset, currentOffset + 20))
    currentOffset += 20

    val scFee: Long = BytesUtils.getReversedLong(bwtRequestOutputBytes, currentOffset)
    currentOffset += 8

    val scProofSize: Int = CryptoLibProvider.sigProofThresholdCircuitFunctions.proofSizeLength()
    val scProof: Array[Byte] = bwtRequestOutputBytes.slice(currentOffset, currentOffset + scProofSize)
    currentOffset += scProofSize

    new MainchainTxBwtRequestCrosschainOutput(bwtRequestOutputBytes.slice(offset, currentOffset),
      sidechainId, scRequestData, mcDestinationAddress, scFee, scProof)
  }
}

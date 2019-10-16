package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils}

import scala.util.Try

class MainchainTxSidechainCreationCrosschainOutput(
                                                    val sidechainCreationOutputBytes: Array[Byte],
                                                    override val sidechainId: Array[Byte],
                                                    val withdrawalEpochLength: Int,
                                                  ) extends MainchainTxCrosschainOutput {
  override val outputType: Byte = MainchainTxSidechainCreationCrosschainOutput.OUTPUT_TYPE

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(sidechainCreationOutputBytes))
}


object MainchainTxSidechainCreationCrosschainOutput {
  val OUTPUT_TYPE: Byte = 3.toByte
  val SIDECHAIN_CREATION_OUTPUT_SIZE = 36 // 32 + 4

  def create(sidechainCreationOutputBytes: Array[Byte], offset: Int): Try[MainchainTxSidechainCreationCrosschainOutput] = Try {
    if(offset < 0 || sidechainCreationOutputBytes.length - offset < SIDECHAIN_CREATION_OUTPUT_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val withdrawalEpochLength: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 4

    new MainchainTxSidechainCreationCrosschainOutput(sidechainCreationOutputBytes.slice(offset, currentOffset), sidechainId, withdrawalEpochLength)
  }
}

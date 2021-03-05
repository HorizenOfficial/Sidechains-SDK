package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils}

import scala.util.Try

class MainchainTxForwardTransferCrosschainOutput(
                                        val forwardTransferOutputBytes: Array[Byte],
                                        override val sidechainId: Array[Byte],
                                        val amount: Long,
                                        val propositionBytes: Array[Byte]
                                      ) extends MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(forwardTransferOutputBytes))
}


object MainchainTxForwardTransferCrosschainOutput {
  val FORWARD_TRANSFER_OUTPUT_SIZE = 72 // 8 + 32 + 32

  def create(forwardTransferOutputBytes: Array[Byte], offset: Int): Try[MainchainTxForwardTransferCrosschainOutput] = Try {
    if(offset < 0 || forwardTransferOutputBytes.length - offset < FORWARD_TRANSFER_OUTPUT_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(forwardTransferOutputBytes, currentOffset)
    currentOffset += 8

    val propositionBytes: Array[Byte] = BytesUtils.reverseBytes(forwardTransferOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(forwardTransferOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    new MainchainTxForwardTransferCrosschainOutput(forwardTransferOutputBytes.slice(offset, currentOffset), sidechainId, amount, propositionBytes)
  }
}


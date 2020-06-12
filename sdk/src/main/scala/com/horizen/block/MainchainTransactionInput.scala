package com.horizen.block

import com.horizen.utils.{BytesUtils, VarInt}

case class MainchainTransactionInput(inputBytes: Array[Byte],
                                     prevTxHash: Array[Byte],
                                     prevTxOutputIndex: Int,
                                     txScript: Array[Byte],
                                     sequence: Int) {
  def size: Int = inputBytes.length
}

object MainchainTransactionInput {
  def parse(inputBytes: Array[Byte], offset: Int): MainchainTransactionInput = {
    var currentOffset: Int = offset
    val prevTxHash: Array[Byte] = inputBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val prevTxOutputIndex: Int = BytesUtils.getInt(inputBytes, currentOffset)
    currentOffset += 4

    val scriptLength: VarInt = BytesUtils.getReversedVarInt(inputBytes, currentOffset)
    currentOffset += scriptLength.size()

    val txScript: Array[Byte] = inputBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
    currentOffset += scriptLength.value().intValue()

    val sequence: Int = BytesUtils.getInt(inputBytes, currentOffset)
    currentOffset += 4

    MainchainTransactionInput(inputBytes.slice(offset, currentOffset), prevTxHash, prevTxOutputIndex, txScript, sequence)
  }
}




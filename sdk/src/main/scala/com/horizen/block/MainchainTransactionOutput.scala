package com.horizen.block

import com.horizen.utils.{BytesUtils, VarInt}

case class MainchainTransactionOutput(outputBytes: Array[Byte], value: Long, script: Array[Byte]) {
  def size: Int = outputBytes.length
}

object MainchainTransactionOutput {
  def parse(outputBytes: Array[Byte], offset: Int): MainchainTransactionOutput = {

    var currentOffset: Int = offset

    val value: Long = BytesUtils.getReversedLong(outputBytes, currentOffset)
    currentOffset += 8

    val scriptLength: VarInt = BytesUtils.getVarInt(outputBytes, currentOffset)
    currentOffset += scriptLength.size()

    val script: Array[Byte] = BytesUtils.reverseBytes(outputBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue()))
    currentOffset += scriptLength.value().intValue()

    new MainchainTransactionOutput(outputBytes.slice(offset, currentOffset), value, script)
  }
}




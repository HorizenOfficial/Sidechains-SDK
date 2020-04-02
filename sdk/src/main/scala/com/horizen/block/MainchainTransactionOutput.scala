package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils, VarInt}

case class MainchainTransactionOutput
  (outputBytes: Array[Byte],
   pubKeyHash: Array[Byte],
   amount: Long)
{

  def size: Int = outputBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(outputBytes))

}

object MainchainTransactionOutput {
  def parse(outputBytes: Array[Byte], offset: Int): MainchainTransactionOutput = {

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(outputBytes, currentOffset)
    currentOffset += 8

    val scriptLength: VarInt = BytesUtils.getVarInt(outputBytes, currentOffset)
    currentOffset += scriptLength.size()

    val pubKeyHash: Array[Byte] = BytesUtils.reverseBytes(outputBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue()))
    currentOffset += scriptLength.value().intValue()

    new MainchainTransactionOutput(outputBytes.slice(offset, currentOffset), pubKeyHash, amount)
  }
}




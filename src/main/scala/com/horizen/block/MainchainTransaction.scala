package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils, VarInt}

class MainchainTransaction(
                          transactionsBytes: Array[Byte],
                          offset: Int
                          ) {
  parse()

  private var _size: Int = 0
  private var _version: Int = 0
  private var _marker: Byte = 0
  private var _flag: Byte = 0
  private var _useSegwit: Boolean = false
  private var _inputs: Seq[MainchainTxInput] = Seq()
  private var _outputs: Seq[MainchainTxOutput] = Seq()
  private var _lockTime: Int = 0

  lazy val bytes: Array[Byte] = transactionsBytes.slice(offset, offset + _size)

  lazy val hash: Array[Byte] = Utils.doubleSHA256Hash(bytes)

  def size = _size


  private def parse() = {
    var currentOffset: Int = offset

    _version = BytesUtils.getInt(transactionsBytes, currentOffset)
    currentOffset += 4

    _marker = transactionsBytes(currentOffset)
    currentOffset += 1

    _flag = transactionsBytes(currentOffset)
    currentOffset += 1

    // parse inputs
    val inputsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
    currentOffset += inputsNumber.size()

    for (i <- 1 to inputsNumber.value().intValue()) {
      val prevTxHash: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + 32)
      currentOffset += 32

      val prevTxOuputIndex: Int = BytesUtils.getInt(transactionsBytes, currentOffset)
      currentOffset += 4

      val scriptLength: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += scriptLength.size()

      val txScript: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
      currentOffset += scriptLength.value().intValue()

      val sequence: Int = BytesUtils.getInt(transactionsBytes, currentOffset)
      currentOffset += 4

      _inputs = _inputs :+ new MainchainTxInput(prevTxHash, prevTxOuputIndex, txScript, sequence)
    }

    // parse outputs
    val outputsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
    currentOffset += outputsNumber.size()

    for (i <- 1 to outputsNumber.value().intValue()) {
      val value: Long = BytesUtils.getLong(transactionsBytes, currentOffset)
      currentOffset += 8

      val scriptLength: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += scriptLength.size()

      val script: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
      currentOffset += scriptLength.value().intValue()

      _outputs = _outputs :+ new MainchainTxOutput(value, script)
    }

    if(_marker == 0)
      _useSegwit = true


    // parse witness
    // Note: actually witness data is not important for us. So we will just parse it for knowing its size.
    if(_useSegwit) {
      val witnessNumber: Long = inputsNumber.value()
      for (i <- 1 to witnessNumber.intValue()) {
        val pushCount: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
        currentOffset += pushCount.size()
        for( j <- 1 to pushCount.value().intValue()) {
          val pushSize: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
          currentOffset += pushSize.size() + pushSize.value().intValue()
        }
      }
    }

    _lockTime = BytesUtils.getInt(transactionsBytes, currentOffset)
    currentOffset += 4

    _size = currentOffset - offset
  }
}


// Note: Witness data is ignored during parsing.
class MainchainTxInput(
                        val prevTxHash: Array[Byte],
                        val prevTxOutIndex: Int,
                        val txScript: Array[Byte],
                        val sequence: Int
                      ) {
}


class MainchainTxOutput(
                        val value: Long,
                        val script: Array[Byte]
                       ) {

}
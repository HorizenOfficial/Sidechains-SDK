package com.horizen.block

trait MainchainTxCrosschainOutput {
  val outputType: Byte
  val amount: Long
  val nonce: Long
  val propositionBytes: Array[Byte]
  val sidechainId: Array[Byte]
  val hash: Array[Byte]
}
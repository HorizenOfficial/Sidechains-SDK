package com.horizen.block

trait MainchainTxCrosschainOutput {
  val sidechainId: Array[Byte]
  val hash: Array[Byte]
}
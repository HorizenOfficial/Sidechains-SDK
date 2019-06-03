package com.horizen.block

import com.horizen.proposition.PublicKey25519Proposition

trait MainchainTxCrosschainOutput {
  val outputType: Byte
  val amount: Long
  val proposition: PublicKey25519Proposition
  val sidechainId: Array[Byte]
  val hash: Array[Byte]
}
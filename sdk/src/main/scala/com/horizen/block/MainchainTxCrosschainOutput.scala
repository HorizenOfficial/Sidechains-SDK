package com.horizen.block

import com.horizen.utils.BytesUtils

trait MainchainTxCrosschainOutput {
  val sidechainId: Array[Byte]
  val hash: Array[Byte]

  // Return Hex representation of Sidechain in Big Endian form same as MC RPC.
  final def sidechainIdBigEndianHex(): String = {
    BytesUtils.toHexString(BytesUtils.reverseBytes(sidechainId))
  }
}
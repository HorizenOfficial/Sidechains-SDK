package io.horizen.block

import com.horizen.utils.BytesUtils

trait MainchainTxCrosschainOutput {
  // In Little Endian as in MC
  val sidechainId: Array[Byte]
  // In Little Endian as in MC
  val hash: Array[Byte]

  // Return Hex representation of Sidechain in Big Endian form same as MC RPC.
  final def sidechainIdBigEndianHex(): String = {
    BytesUtils.toHexString(BytesUtils.reverseBytes(sidechainId))
  }
}
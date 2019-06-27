package com.horizen.fixtures

import com.google.common.primitives.{Bytes, Longs}
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.utils.BytesUtils

trait MainchainTxCrosschainOutputFixture {
  def generateMainchainTxForwardTransferCrosschainOutputBytes(amount: Long, nonce: Long, proposition: PublicKey25519Proposition,
                                                              sidechainId: Array[Byte]): Array[Byte] = {
    Bytes.concat(
      BytesUtils.reverseBytes(Longs.toByteArray(amount)),
      BytesUtils.reverseBytes(Longs.toByteArray(nonce)),
      BytesUtils.reverseBytes(proposition.bytes),
      BytesUtils.reverseBytes(sidechainId)
    )
  }

  def generateMainchainTxCertifierLockCrosschainOutputBytes(amount: Long, nonce: Long, proposition: PublicKey25519Proposition,
                                                            sidechainId: Array[Byte], withdrawalEpoch: Long): Array[Byte] = {
    Bytes.concat(
      BytesUtils.reverseBytes(Longs.toByteArray(amount)),
      BytesUtils.reverseBytes(Longs.toByteArray(nonce)),
      BytesUtils.reverseBytes(proposition.bytes),
      BytesUtils.reverseBytes(sidechainId),
      BytesUtils.reverseBytes(Longs.toByteArray(withdrawalEpoch))
    )
  }
}

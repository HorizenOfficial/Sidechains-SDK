package com.horizen.fixtures

import com.google.common.primitives.{Bytes, Longs}
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.utils.BytesUtils

trait MainchainTxCrosschainOutputFixture {
  def generateMainchainTxForwardTransferCrosschainOutputBytes(amount: Long,
                                                              proposition: PublicKey25519Proposition,
                                                              sidechainId: Array[Byte],
                                                              mcReturnAddress: Array[Byte]): Array[Byte] = {
    Bytes.concat(
      BytesUtils.reverseBytes(Longs.toByteArray(amount)),
      proposition.bytes,
      sidechainId,
      mcReturnAddress
    )
  }
}

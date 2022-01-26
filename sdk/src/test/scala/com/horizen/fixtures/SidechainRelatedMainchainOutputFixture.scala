package com.horizen.fixtures

import com.horizen.block.MainchainTxForwardTransferCrosschainOutput
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.transaction.mainchain.ForwardTransfer
import com.horizen.utils.BytesUtils

import scala.util.Random

trait SidechainRelatedMainchainOutputFixture extends SecretFixture {

  def getForwardTransfer(proposition: PublicKey25519Proposition, sidechainId: Array[Byte]): ForwardTransfer = {
    val output = new MainchainTxForwardTransferCrosschainOutput(new Array[Byte](1), sidechainId,
      Random.nextInt(10000), BytesUtils.reverseBytes(proposition.bytes()), getMcReturnAddress)
    val forwardTransferHash = new Array[Byte](32)
    Random.nextBytes(forwardTransferHash)

    new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
  }
}

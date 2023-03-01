package io.horizen.fixtures

import io.horizen.block.{MainchainTxForwardTransferCrosschainOutput, MainchainTxSidechainCreationCrosschainOutput}
import io.horizen.proposition.PublicKey25519Proposition
import io.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import io.horizen.utils.BytesUtils
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.util.Random

trait SidechainRelatedMainchainOutputFixture extends SecretFixture {

  def getForwardTransfer(proposition: PublicKey25519Proposition, sidechainId: Array[Byte], seed: Long = 12345): ForwardTransfer = {
    Random.setSeed(seed)
    val output = new MainchainTxForwardTransferCrosschainOutput(new Array[Byte](1), sidechainId,
      Random.nextInt(10000), BytesUtils.reverseBytes(proposition.bytes()), getMcReturnAddress)
    val forwardTransferHash = new Array[Byte](32)
    Random.nextBytes(forwardTransferHash)

    new ForwardTransfer(output, forwardTransferHash, Random.nextInt(100))
  }

  def getDummyScCreation(sidechainId: Array[Byte]): SidechainCreation = {
    val output = new MainchainTxSidechainCreationCrosschainOutput(sidechainId, mock[MainchainTxSidechainCreationCrosschainOutput])
    new SidechainCreation(output, new Array[Byte](32), 0)
  }
}

package io.horizen.account.fixtures


import com.google.common.primitives.Longs
import io.horizen.account.sc2sc.AccountCrossChainMessage
import io.horizen.fixtures.SecretFixture

import scala.util.Random

trait AccountCrossChainMessageFixture extends SecretFixture{

  def getRandomAccountCrossMessage(seed: Long): AccountCrossChainMessage ={
    val random: Random = new Random(seed)
    val receiverSidechain = new Array[Byte](32)
    random.nextBytes(receiverSidechain)
    val receiverAddress = new Array[Byte](20)
    random.nextBytes(receiverAddress)
    val payloadHash = new Array[Byte](32)
    random.nextBytes(payloadHash)
    AccountCrossChainMessage(
      1,
      getPrivateKey25519(Longs.toByteArray(random.nextLong())).publicImage().pubKeyBytes(),
      receiverSidechain,
      receiverAddress,
      payloadHash
    )
  }
}
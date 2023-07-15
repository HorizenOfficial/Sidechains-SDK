package io.horizen.account.fixtures


import com.google.common.primitives.Longs
import io.horizen.account.sc2sc.AccountCrossChainMessage
import io.horizen.fixtures.SecretFixture

import scala.util.Random

trait AccountCrossChainMessageFixture extends SecretFixture{

  def getRandomAccountCrossMessage(seed: Long): AccountCrossChainMessage ={
    val random: Random = new Random(seed)
    val senderSidechain = new Array[Byte](32)
    random.nextBytes(senderSidechain)
    val receiverSidechain = new Array[Byte](32)
    random.nextBytes(receiverSidechain)
    val receiverAddress = new Array[Byte](20)
    random.nextBytes(receiverAddress)
    val payload = new Array[Byte](32)
    random.nextBytes(payload)
    AccountCrossChainMessage(
      1,
      senderSidechain,
      getPrivateKey25519(Longs.toByteArray(random.nextLong())).publicImage().pubKeyBytes(),
      receiverSidechain,
      receiverAddress,
      payload
    )
  }
}
package com.horizen.fixtures

import java.util.Random

import com.horizen.box.ForgerBox
import com.horizen.secret.PrivateKey25519
import com.horizen.utils.Ed25519
import com.horizen.vrf.{VRFKeyGenerator, VRFPublicKey}
import com.horizen.{SidechainTypes, utils}

class ForgerBoxFixture extends SecretFixture with SidechainTypes{

}

object ForgerBoxFixture {
  def generateForgerBox(seed: Long): (ForgerBox, PrivateKey25519) = {
    val randomGenerator = new Random(seed)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(byteSeed)
    val ownerKeys: PrivateKey25519 = new PrivateKey25519(propositionKeyPair.getKey, propositionKeyPair.getValue)
    val nonce: Long = randomGenerator.nextLong
    val value: Long = randomGenerator.nextLong
    val vrfPubKey: VRFPublicKey = VRFKeyGenerator.generate(ownerKeys.bytes())._2
    val proposition = ownerKeys.publicImage()
    val forgerBox = new ForgerBox(proposition, nonce, value, proposition, vrfPubKey)
    (forgerBox, ownerKeys)
  }
}
package com.horizen.fixtures

import java.util.Random

import com.horizen.box.ForgerBox
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.utils.Ed25519
import com.horizen.vrf.{VRFKeyGenerator, VRFPublicKey}
import com.horizen.{SidechainTypes, utils}

class ForgerBoxFixture extends SecretFixture with SidechainTypes{

}

object ForgerBoxFixture {
  def generateForgerBox(seed: Long): ForgerBox = {
    val randomGenerator = new Random(seed)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(byteSeed)
    val proposition: PublicKey25519Proposition = new PublicKey25519Proposition(propositionKeyPair.getValue)
    val nonce: Long = randomGenerator.nextLong
    val value: Long = randomGenerator.nextLong
    val rewardKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(propositionKeyPair.getKey)
    val rewardProposition: PublicKey25519Proposition = new PublicKey25519Proposition(rewardKeyPair.getValue)
    val vrfPubKey: VRFPublicKey = VRFKeyGenerator.generate(rewardKeyPair.getKey)._2
    new ForgerBox(proposition, nonce, value, rewardProposition, vrfPubKey)
  }

  def generateForgerBox(vrfPubKey: VRFPublicKey, value: Long, seed: Long): ForgerBox = {
    //val randomGenerator = new Random(seed)
    val byteSeed: Array[Byte] = Array.fill(32)(seed.toByte)
    val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(byteSeed)
    val proposition: PublicKey25519Proposition = new PublicKey25519Proposition(propositionKeyPair.getValue)
    val rewardKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(propositionKeyPair.getKey)
    val rewardProposition: PublicKey25519Proposition = new PublicKey25519Proposition(rewardKeyPair.getValue)
    new ForgerBox(proposition, seed, value, rewardProposition, vrfPubKey)
  }

  def generateForgerBox: ForgerBox = generateForgerBox(new Random().nextLong())
}
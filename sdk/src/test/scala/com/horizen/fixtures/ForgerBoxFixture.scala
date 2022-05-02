package com.horizen.fixtures

import java.util.Random

import com.horizen.box.ForgerBox
import com.horizen.box.data.ForgerBoxData
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.{PrivateKey25519, VrfKeyGenerator, VrfSecretKey}
import com.horizen.utils
import com.horizen.utils.Ed25519

case class ForgerBoxGenerationMetadata(propositionSecret: PrivateKey25519, blockSignSecret: PrivateKey25519, vrfSecret: VrfSecretKey,
                                       forgingStakeInfo: ForgingStakeInfo)

object ForgerBoxFixture {
  def generateForgerBox(seed: Long): (ForgerBox, ForgerBoxGenerationMetadata) = generateForgerBox(seed, None)

  def generateForgerBox(seed: Long,
                        vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)]): (ForgerBox, ForgerBoxGenerationMetadata) = {
    val randomGenerator = new Random(seed)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(byteSeed)
    val ownerKeys: PrivateKey25519 = new PrivateKey25519(propositionKeyPair.getKey, propositionKeyPair.getValue)
    val value: Long = Math.abs(randomGenerator.nextLong)
    val (vrfSecret, vrfPubKey) = vrfKeysOpt.getOrElse{
      val secretKey = VrfKeyGenerator.getInstance().generateSecret(ownerKeys.bytes())
      val publicKey = secretKey.publicImage()
        (secretKey, publicKey)
    }
    val proposition = ownerKeys.publicImage()

    val forgerBoxData = new ForgerBoxData(proposition, value, proposition, vrfPubKey)
    val nonce: Long = randomGenerator.nextLong

    val forgerBox = forgerBoxData.getBox(nonce)
    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfo(forgerBox.blockSignProposition(), forgerBox.vrfPubKey(), forgerBox.value())
    (forgerBox, ForgerBoxGenerationMetadata(ownerKeys, ownerKeys, vrfSecret, forgingStakeInfo))
  }
}
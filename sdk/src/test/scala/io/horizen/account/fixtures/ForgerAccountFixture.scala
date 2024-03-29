package io.horizen.account.fixtures

import io.horizen.account.proposition.AddressProposition
import io.horizen.account.utils.AccountPayment
import io.horizen.consensus.ForgingStakeInfo
import io.horizen.fixtures.SecretFixture
import io.horizen.proposition.VrfPublicKey
import io.horizen.secret.{PrivateKey25519, VrfKeyGenerator, VrfSecretKey}
import io.horizen.utils
import io.horizen.utils.Ed25519
import java.math.BigInteger
import java.util
import java.util.Random

case class ForgerAccountGenerationMetadata(propositionSecret: PrivateKey25519, blockSignSecret: PrivateKey25519, vrfSecret: VrfSecretKey,
                                       forgingStakeInfo: ForgingStakeInfo)

object ForgerAccountFixture extends SecretFixture {
  def generateForgerAccountData(seed: Long): (AccountPayment, ForgerAccountGenerationMetadata) = generateForgerAccountData(seed, None)

  def generateForgerAccountData(seed: Long,
                        vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)]): (AccountPayment, ForgerAccountGenerationMetadata) = {
    val randomGenerator = new Random(seed)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val signerKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(byteSeed)
    val signerPrivKey: PrivateKey25519 = new PrivateKey25519(signerKeyPair.getKey, signerKeyPair.getValue)
    val value: Long = Math.abs(randomGenerator.nextLong)
    val (vrfSecret, vrfPubKey) = vrfKeysOpt.getOrElse{
      val vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(signerPrivKey.bytes())
      val vrfPublicKey = vrfSecretKey.publicImage()
        (vrfSecretKey, vrfPublicKey)
    }
    val blockSignProposition = signerPrivKey.publicImage()

    // TODO get a deterministic value with createEcKeyPair
    val ownerAddressProposition = new AddressProposition(util.Arrays.copyOf(byteSeed, AddressProposition.LENGTH))

    val accountPayment = AccountPayment(ownerAddressProposition, BigInteger.valueOf(value))
    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfo(blockSignProposition, vrfPubKey, value)
    (accountPayment, ForgerAccountGenerationMetadata(signerPrivKey, signerPrivKey, vrfSecret, forgingStakeInfo))
  }

  def getAccountPayment(seed: Long): AccountPayment = {
    AccountPayment(getAddressProposition(seed), BigInteger.valueOf(scala.util.Random.nextInt(100)))
  }
}

package com.horizen.account.fixtures

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.utils.AccountPayment

import java.util.Random
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.fixtures.sidechainblock.generation.SidechainBlocksGenerator.txGen.getAddressProposition
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.{PrivateKey25519, VrfKeyGenerator, VrfSecretKey}
import com.horizen.utils
import com.horizen.utils.{BytesUtils, Ed25519}
import org.web3j.crypto.{ECKeyPair, Keys}

import java.math.BigInteger


case class ForgerAccountGenerationMetadata(propositionSecret: PrivateKey25519, blockSignSecret: PrivateKey25519, vrfSecret: VrfSecretKey,
                                       forgingStakeInfo: ForgingStakeInfo)

object ForgerAccountFixture {
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


    // create private/public key pair
    val pair: ECKeyPair = Keys.createEcKeyPair
    val ownerAddressProposition = new AddressProposition(BytesUtils.fromHexString(Keys.getAddress(pair)))

    val accountPayment = AccountPayment(ownerAddressProposition, BigInteger.valueOf(value))
    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfo(blockSignProposition, vrfPubKey, value)
    (accountPayment, ForgerAccountGenerationMetadata(signerPrivKey, signerPrivKey, vrfSecret, forgingStakeInfo))
  }

  def getAccountPayment(seed: Long): AccountPayment = {
    AccountPayment(getAddressProposition(seed), BigInteger.valueOf(scala.util.Random.nextInt(100)))
  }
}
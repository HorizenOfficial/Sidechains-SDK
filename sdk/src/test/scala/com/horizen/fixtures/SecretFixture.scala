package com.horizen.fixtures

import com.google.common.primitives.Longs
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.utils.Secp256k1
import com.horizen.customtypes._
import java.util.{ArrayList => JArrayList, List => JList}
import com.horizen.proof.Signature25519
import com.horizen.proposition.{MCPublicKeyHashProposition, VrfPublicKey}
import com.horizen.schnorrnative.{SchnorrKeyPair, SchnorrSecretKey}
import java.math.BigInteger
import com.horizen.secret._
import java.nio.charset.StandardCharsets
import java.util
import scala.util.Random

trait SecretFixture {
  val pkc: PrivateKey25519Creator = PrivateKey25519Creator.getInstance()
  val schnorr: SchnorrKeyGenerator = SchnorrKeyGenerator.getInstance()
  val pkk1: PrivateKeySecp256k1Creator = PrivateKeySecp256k1Creator.getInstance()
  val vrf: VrfKeyGenerator = VrfKeyGenerator.getInstance()

  val pk1: PrivateKey25519 = pkc.generateSecret("seed1".getBytes(StandardCharsets.UTF_8))
  val pk2: PrivateKey25519 = pkc.generateSecret("seed2".getBytes(StandardCharsets.UTF_8))
  val pk3: PrivateKey25519 = pkc.generateSecret("seed3".getBytes(StandardCharsets.UTF_8))
  val pk4: PrivateKey25519 = pkc.generateSecret("seed4".getBytes(StandardCharsets.UTF_8))
  val pk5: PrivateKey25519 = pkc.generateSecret("seed5".getBytes(StandardCharsets.UTF_8))
  val pk6: PrivateKey25519 = pkc.generateSecret("seed6".getBytes(StandardCharsets.UTF_8))
  val pk7: PrivateKey25519 = pkc.generateSecret("seed7".getBytes(StandardCharsets.UTF_8))
  val schnorrPk: SchnorrSecret = schnorr.generateSecret("seed8".getBytes(StandardCharsets.UTF_8))

  def getPrivateKey25519: PrivateKey25519 = {
    val seed = new Array[Byte](32)
    Random.nextBytes(seed)
    pkc.generateSecret(seed)
  }

  def getPrivateKeySecp256k1: PrivateKeySecp256k1 = {
    val seed = new Array[Byte](32)
    Random.nextBytes(seed)
    pkk1.generateSecret(seed)
  }

  def getSchnorrKey: SchnorrSecret = {
    val seed = new Array[Byte](32)
    Random.nextBytes(seed)
    schnorr.generateSecret(seed)
  }

  def getVrfKey: VrfSecretKey = {
    val seed = new Array[Byte](32)
    Random.nextBytes(seed)
    vrf.generateSecret(seed)
  }

  def getPrivateKey25519(seed: Array[Byte]): PrivateKey25519 = {
    pkc.generateSecret(seed)
  }

  def getPrivateKeySecp256k1(seed: Array[Byte]): PrivateKeySecp256k1 = {
    pkk1.generateSecret(seed)
  }

  def getSchnorrKey(seed: Array[Byte]): SchnorrSecret = {
    schnorr.generateSecret(seed)
  }

  def getVrfKey(seed: Array[Byte]): VrfSecretKey = {
    vrf.generateSecret(seed)
  }

  def getPrivateKey25519List(count: Int): JList[PrivateKey25519] = {
    val seed = new Array[Byte](32)
    val keysList : JList[PrivateKey25519] = new JArrayList()
    for (_ <- 1 to count) {
      Random.nextBytes(seed)
      keysList.add(pkc.generateSecret(seed))
    }
    keysList
  }

  def getRandomCustomProof: CustomProof = {
    new CustomProof(Random.nextInt())
  }

  def getRandomSignature25519: Signature25519 = {
    val pk = getPrivateKey25519
    val message = "12345".getBytes(StandardCharsets.UTF_8)
    pk.sign(message)
  }

  def getCustomPrivateKey: CustomPrivateKey = {
    val privateBytes = new Array[Byte](CustomPrivateKey.PRIVATE_KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.PUBLIC_KEY_LENGTH)

    Random.nextBytes(privateBytes)
    Random.nextBytes(publicBytes)

    new CustomPrivateKey(privateBytes, publicBytes)
  }

  def getCustomPrivateKeyList(count: Int): JList[CustomPrivateKey] = {
    val privateBytes = new Array[Byte](CustomPrivateKey.PRIVATE_KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.PUBLIC_KEY_LENGTH)
    val keysList: JList[CustomPrivateKey] = new JArrayList()

    for (_ <- 1 to count) {
      Random.nextBytes(privateBytes)
      Random.nextBytes(publicBytes)

      keysList.add(new CustomPrivateKey(privateBytes, publicBytes))
    }

    keysList
  }

  def getMCPublicKeyHashProposition: MCPublicKeyHashProposition = {
    val keyHashBytes = new Array[Byte](MCPublicKeyHashProposition.KEY_LENGTH)
    Random.nextBytes(keyHashBytes)

    new MCPublicKeyHashProposition(keyHashBytes)
  }

  def getMcReturnAddress: Array[Byte] = {
    val address = new Array[Byte](MCPublicKeyHashProposition.KEY_LENGTH)
    Random.nextBytes(address)

    address
  }

  def getMCPublicKeyHashPropositionList(count: Int): JList[MCPublicKeyHashProposition] = {
    val keyList = new JArrayList[MCPublicKeyHashProposition]()

    for (_ <- 1 to count)
      keyList.add(getMCPublicKeyHashProposition)

    keyList
  }

  def getVRFPublicKey: VrfPublicKey = {
    VrfKeyGenerator.getInstance().generateSecret(Random.nextString(32).getBytes(StandardCharsets.UTF_8)).publicImage()
  }

  def getVRFPublicKey(seed: Long): VrfPublicKey = {
    VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes(StandardCharsets.UTF_8)).publicImage()
  }

  def getPrivateKeySecp256k1(seed: Long): PrivateKeySecp256k1 = {
    val pair = Secp256k1.createKeyPair(Longs.toByteArray(seed))
    val privateKey = util.Arrays.copyOf(pair.getKey, Secp256k1.PRIVATE_KEY_SIZE)
    new PrivateKeySecp256k1(privateKey)
  }

  def getAddressProposition(seed: Long): AddressProposition = {
    getPrivateKeySecp256k1(seed).publicImage()
  }

  def buildSchnorrPrivateKey(index: Int): SchnorrSecretKey = {
    SchnorrKeyPair.generate(BigInteger.valueOf(index).toByteArray).getSecretKey
  }
}

class SecretFixtureClass extends SecretFixture

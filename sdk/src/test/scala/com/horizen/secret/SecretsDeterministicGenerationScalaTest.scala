package com.horizen.secret

import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.utils.Secp256k1
import com.horizen.cryptolibprovider.{CryptoLibProvider, VrfFunctions}
import com.horizen.cryptolibprovider.utils.SchnorrFunctions
import com.horizen.utils.Ed25519
import org.junit.Assert.{assertEquals, assertNotEquals}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import java.nio.charset.StandardCharsets
import java.util


class SecretsDeterministicGenerationScalaTest
  extends JUnitSuite
{

  val constantTestSeed: Array[Byte] = "12345".getBytes(StandardCharsets.UTF_8)
  val anotherConstantSeed: Array[Byte] = "another seed".getBytes(StandardCharsets.UTF_8)

  def localGetPrivateKey25519(seed: Array[Byte]): PrivateKey25519 = {
    val keyPair: com.horizen.utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(seed)
    new PrivateKey25519(keyPair.getKey, keyPair.getValue)
  }

  def localGetPrivateKeySecp256k1(seed: Array[Byte]): PrivateKeySecp256k1 = {
    val pair: com.horizen.utils.Pair[Array[Byte], Array[Byte]] = Secp256k1.createKeyPair(seed)
    val privateKey = util.Arrays.copyOf(pair.getKey, Secp256k1.PRIVATE_KEY_SIZE)
    new PrivateKeySecp256k1(privateKey)
  }

  def localGetSchnorrSecretKey(seed: Array[Byte]): SchnorrSecret = {
    val schnorrKeys: util.EnumMap[SchnorrFunctions.KeyType, Array[Byte]] =
      CryptoLibProvider.schnorrFunctions.generateSchnorrKeys(seed)
    new SchnorrSecret(schnorrKeys.get(SchnorrFunctions.KeyType.SECRET), schnorrKeys.get(SchnorrFunctions.KeyType.PUBLIC))
  }

  def localGetVrfSecretKey(seed: Array[Byte]): VrfSecretKey = {
    val vrfKeys : util.EnumMap[VrfFunctions.KeyType, Array[Byte]] =
      CryptoLibProvider.vrfFunctions.generatePublicAndSecretKeys(seed)
    new VrfSecretKey(vrfKeys.get(VrfFunctions.KeyType.SECRET), vrfKeys.get(VrfFunctions.KeyType.PUBLIC))
  }

  @Test
  def testSecret25519(): Unit = {
    val s1 : Secret = localGetPrivateKey25519(constantTestSeed)

    val anotherSecret : Secret = localGetPrivateKey25519(anotherConstantSeed)
    assertNotEquals("Secrets should NOT be equal", s1, anotherSecret)

    val s2 : Secret = localGetPrivateKey25519(constantTestSeed)
    assertEquals("Secrets should be equal", s1, s2)
  }

  @Test
  def testSecretSchnorr(): Unit = {
    val s1 : Secret = localGetSchnorrSecretKey(constantTestSeed)

    val anotherSecret : Secret = localGetSchnorrSecretKey(anotherConstantSeed)
    assertNotEquals("Secrets should NOT be equal", s1, anotherSecret)

    val s2 : Secret = localGetSchnorrSecretKey(constantTestSeed)
    assertEquals("Secrets should be equal", s1, s2)
  }

  @Test
  def testSecretVrf(): Unit = {
    val s1 : Secret = localGetVrfSecretKey(constantTestSeed)

    val anotherSecret : Secret = localGetVrfSecretKey(anotherConstantSeed)
    assertNotEquals("Secrets should NOT be equal", s1, anotherSecret)

    val s2 : Secret = localGetVrfSecretKey(constantTestSeed)
    assertEquals("Secrets should be equal", s1, s2)
  }

  @Test
  def testSecretSecp256k(): Unit = {
    val s1 : Secret = localGetPrivateKeySecp256k1(constantTestSeed)

    val anotherSecret : Secret = localGetPrivateKeySecp256k1(anotherConstantSeed)
    assertNotEquals("Secrets should NOT be equal", s1, anotherSecret)

    val s2 : Secret = localGetPrivateKeySecp256k1(constantTestSeed)
    assertEquals("Secrets should be equal", s1, s2)
  }

}

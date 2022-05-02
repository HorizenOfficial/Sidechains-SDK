package com.horizen.companion

import org.scalatestplus.junit.JUnitSuite
import org.junit.Test
import org.junit.Assert._
import com.horizen.fixtures._
import com.horizen.customtypes._
import com.horizen.secret._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes

class SidechainSecretsCompanionTest
  extends JUnitSuite
  with SecretFixture
  with SidechainTypes
{

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  customSecretSerializers.put(CustomPrivateKey.SECRET_TYPE_ID, CustomPrivateKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])

  val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)
  val sidechainSecretsCompanionCore = SidechainSecretsCompanion(new JHashMap())

  @Test def testCore(): Unit = {
    val secret = getPrivateKey25519

    val secretBytes = sidechainSecretsCompanion.toBytes(secret)

    assertNotEquals("Secret must have core type.", secretBytes(0), Byte.MaxValue)
    assertEquals("Secret must have registered core typeId.", secretBytes(0), secret.secretTypeId())
    assertEquals("Deserialization must return same Secret.", secret, sidechainSecretsCompanion.parseBytesTry(secretBytes).get)
  }

  @Test def testRegisteredCustom(): Unit = {
    val customSecret = getCustomPrivateKey

    val customSecretBytes = sidechainSecretsCompanion.toBytes(customSecret)

    assertEquals("Secret must have custom type.", customSecretBytes(0), Byte.MaxValue)
    assertEquals("Secret must have registered custom typeId.", customSecretBytes(1), customSecret.secretTypeId())
    assertEquals("Deserialization must return same Secret.", customSecret, sidechainSecretsCompanion.parseBytesTry(customSecretBytes).get)
  }

  @Test def testUnregisteredCustom(): Unit = {
    val customSecret = getCustomPrivateKey
    var exceptionThrown = false


    // Test 1: try to serialize custom type Secret. Serialization exception expected, because of custom type is unregistered.
    try {
      sidechainSecretsCompanionCore.toBytes(customSecret)
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertTrue("Exception during serialization for unregistered type of Secret must be thrown.", exceptionThrown)


    // Test 2: try to deserialize custom type Secret. Serialization exception expected, because of custom type is unregistered.
    exceptionThrown = false
    val customSecretBytes = sidechainSecretsCompanion.toBytes(customSecret)

    try {
      sidechainSecretsCompanionCore.parseBytesTry(customSecretBytes).get
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertTrue("Exception during deserialization for unregistered type of Secret must be thrown.", exceptionThrown)
  }
}
package com.horizen.companion

import scala.collection.mutable._

import org.scalatest.junit.JUnitSuite

import org.junit.Test
import org.junit.Assert._

import com.horizen.fixtures._
import com.horizen.customtypes._
import com.horizen.secret._
import com.horizen.proposition._

class SidechainSecretsCompanionTest
  extends JUnitSuite
  with SecretFixture
{

  val customSecretSerializers: Map[Byte, SecretSerializer[_ <: Secret]] =
    Map(CustomPrivateKey.SECRET_TYPE_ID ->  CustomPrivateKeySerializer.getSerializer)
  val sidechainSecretsCompanion = new SidechainSecretsCompanion(customSecretSerializers)
  val sidechainSecretsCompanionCore = new SidechainSecretsCompanion(Map())

  @Test def testCore(): Unit = {
    val s = getSecret()

    val sb = sidechainSecretsCompanion.toBytes(s)

    assertNotEquals("Secret must have core type.", sb(0), Byte.MaxValue)
    assertEquals("Secret must have registered core typeId.", sb(0), s.secretTypeId())
    assertEquals("Deserialization must return same Secret.", s, sidechainSecretsCompanion.parseBytes(sb).get)
  }

  @Test def testRegisteredCustom(): Unit = {
    val s = getCustomSecret()

    val sb = sidechainSecretsCompanion.toBytes(s)

    assertEquals("Secret must have custom type.", sb(0), Byte.MaxValue)
    assertEquals("Secret must have registered custom typeId.", sb(1), s.secretTypeId())
    assertEquals("Deserialization must return same Secret.", s, sidechainSecretsCompanion.parseBytes(sb).get)
  }

  @Test def testUnregisteredCustom(): Unit = {
    val s = getCustomSecret()
    var exceptionThrown = false

    try {
      val sb = sidechainSecretsCompanionCore.toBytes(s)
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertTrue("Exception during serialization for unregisterd type of Secret must be thrown.", exceptionThrown)

    val sb = sidechainSecretsCompanion.toBytes(s)

    try {
      val s1 = sidechainSecretsCompanionCore.parseBytes(sb)
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertTrue("Exception during deserialization for unregisterd type of Secret must be thrown.", exceptionThrown)
  }
}
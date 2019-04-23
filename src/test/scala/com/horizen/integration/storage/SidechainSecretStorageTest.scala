package com.horizen.integration.storage

import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.customtypes.{CustomPrivateKey, CustomPrivateKeySerializer}
import com.horizen.fixtures._
import com.horizen.secret._
import com.horizen.storage.{IODBStoreAdapter, SidechainSecretStorage}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.collection.JavaConverters._
import scala.collection.mutable.Map

class SidechainSecretStorageTest
  extends JUnitSuite
  with SecretFixture
  with IODBStoreFixture
{

  val customSecretSerializers: Map[Byte, SecretSerializer[_ <: Secret]] =
    Map(CustomPrivateKey.SECRET_TYPE_ID ->  CustomPrivateKeySerializer.getSerializer)
  val sidechainSecretsCompanion = new SidechainSecretsCompanion(customSecretSerializers)
  val sidechainSecretsCompanionCore = new SidechainSecretsCompanion(Map())

  @Test def testCoreTypes(): Unit = {
    val sidechainSecretStorage = new SidechainSecretStorage(new IODBStoreAdapter(getStore()), sidechainSecretsCompanion)
    val secret = getSecret()
    val secretList = getSecretList(3).asScala.toList

    assertTrue("Add operation must be succeessful.", sidechainSecretStorage.add(secret).isSuccess)

    val s = sidechainSecretStorage.get(secret.publicImage())

    assertTrue("Storage must contain added Secret.", s.isDefined)
    assertEquals("Secret in storage must be the same as added.", s.get, secret)
    assertEquals("Storage must contain 1 secret.", sidechainSecretStorage.getAll.count(s => true), 1)

    assertTrue("Operation must be unsuccessful.", sidechainSecretStorage.add(secret).isFailure)

    assertTrue("Add operation must be succeessful.", sidechainSecretStorage.add(secretList).isSuccess)

    assertEquals("Storage must contain all added keys.", sidechainSecretStorage.get(secretList.map(_.publicImage()).toList).count(s => true),
      secretList.count(s => true))

    for (s <- sidechainSecretStorage.get(secretList.map(_.publicImage()).toList)) {
      assertNotEquals("Storage must contain added key.", secretList.indexOf(s),-1)
    }

    assertTrue("Add operation must be unsucceessful.", sidechainSecretStorage.add(secretList).isFailure)

    sidechainSecretStorage.remove(secret.publicImage())

    assertTrue("Storage must not contain Secret after remove operation.", sidechainSecretStorage.get(secret.publicImage()).isEmpty)
    assertEquals("Count of keys in storage must be - " + secretList.count(S => true), sidechainSecretStorage.getAll.count(s => true),
      secretList.count(s => true))

    sidechainSecretStorage.remove(secretList.map(_.publicImage()))

    assertEquals("Storage must be empty.", sidechainSecretStorage.getAll.count(s => true), 0)

    assertTrue("Add operation must be succeessful.", sidechainSecretStorage.add(secret).isSuccess)

    val s1 = sidechainSecretStorage.get(secret.publicImage())

    assertTrue("Storage must contain added Secret.", s1.isDefined)
    assertEquals("Secret in storage must be the same as added.", s1.get, secret)
    assertEquals("Storage must contain 1 secret.", sidechainSecretStorage.getAll.count(s => true), 1)
  }

  @Test
  def testCustomTypes() : Unit = {
    val (store1, dir) = getStoreWithPath()
    val ss1 = new SidechainSecretStorage(new IODBStoreAdapter(store1), sidechainSecretsCompanion)
    val secret = getCustomSecret()
    var exceptionThrown = false
    var ss2 : SidechainSecretStorage = null
    var secret2 : Option[Secret] = null

    assertTrue("Add operation must be succeessful.", ss1.add(secret).isSuccess)

    val s = ss1.get(secret.publicImage())

    assertTrue("Storage must contain added Secret.", s.isDefined)
    assertEquals("Secret in storage must be the same as added.", s.get, secret)
    assertEquals("Storage must contain 1 secret.", ss1.getAll.count(s => true), 1)

    assertTrue("Operation must be unsuccessful.", ss1.add(secret).isFailure)

    store1.close()

    val store2 = getStore(dir)

    try {
      ss2 = new SidechainSecretStorage(new IODBStoreAdapter(store2), sidechainSecretsCompanionCore)
    } catch {
      case e : RuntimeException => exceptionThrown = true
    }

    assertTrue("Exception must be thrown if serializer for custom secret type was not specified.", exceptionThrown)

    exceptionThrown = false

    try {
      ss2 = new SidechainSecretStorage(new IODBStoreAdapter(store2), sidechainSecretsCompanion)
      secret2 = ss2.get(secret.publicImage())
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertFalse("Exception must not be thrown for custom secret type.", exceptionThrown)
    assertTrue("Storage must contain added Secret.", secret2.isDefined)
    assertEquals("Secret in storage must be the same as added.", secret2.get, secret)
  }

}

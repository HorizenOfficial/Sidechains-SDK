package io.horizen.integration.storage

import com.google.common.primitives.{Bytes, Ints}
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import io.horizen.SidechainTypes
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.customtypes.{CustomPrivateKey, CustomPrivateKeySerializer}
import io.horizen.fixtures._
import io.horizen.secret._
import io.horizen.storage.SidechainSecretStorage
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.utils.{ByteArrayWrapper, Pair, Utils}
import org.junit.Assert._
import org.junit.{Assert, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import java.nio.charset.StandardCharsets
import java.util.{Optional, ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.util.Try

class SidechainSecretStorageTest
  extends JUnitSuite
    with SecretFixture
    with StoreFixture
    with SidechainTypes {

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  customSecretSerializers.put(CustomPrivateKey.SECRET_TYPE_ID, CustomPrivateKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
  val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)
  val sidechainSecretsCompanionCore = SidechainSecretsCompanion(new JHashMap())


  @Test
  def testCoreTypes(): Unit = {
    testCoreType(getPrivateKey25519, getPrivateKey25519List(3).asScala.toList)
    testCoreType(VrfKeyGenerator.getInstance().generateSecret("seed".getBytes(StandardCharsets.UTF_8)), getPrivateKey25519List(3).asScala.toList)
  }

  @Test
  def testLoadSecret(): Unit = {
    val mockedAdapter = mock[VersionedLevelDbStorageAdapter]
    val storageData = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

    val secretList: List[SidechainTypes#SCS] = List(getPrivateKey25519)
    for (s <- secretList) {
      val key = Utils.calculateKey(s.publicImage().bytes)
      storageData.add(new Pair[ByteArrayWrapper, ByteArrayWrapper](key,
        new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))))
    }
    Mockito.when(mockedAdapter.getAll).thenReturn(storageData)
    new SidechainSecretStorage(mockedAdapter, sidechainSecretsCompanion)

    val nonce = 1
    val keyTypeSalt = PrivateKey25519Creator.getInstance().salt()
    val nonceKey = Utils.calculateKey(Bytes.concat("nonce".getBytes(StandardCharsets.UTF_8), keyTypeSalt))
    storageData.add(new Pair(nonceKey, new ByteArrayWrapper(Ints.toByteArray(nonce))))
    Mockito.when(mockedAdapter.getAll).thenReturn(storageData)
    new SidechainSecretStorage(mockedAdapter, sidechainSecretsCompanion)
  }


  def testCoreType(secret: Secret, secretList: List[Secret]): Unit = {
    val sidechainSecretStorage = new SidechainSecretStorage(getStorage(), sidechainSecretsCompanion)
    var res: Try[SidechainSecretStorage] = null


    // Test 1: try to add single unique Secret
    res = sidechainSecretStorage.add(secret)
    assertTrue("Add operation must be successful, instead failure occurred:\n %s".format(if (res.isFailure) res.failed.get.getMessage else ""), res.isSuccess)
    // verify changes
    val s = sidechainSecretStorage.get(secret.publicImage())
    assertTrue("Storage must contain added Secret.", s.isDefined)
    assertEquals("Secret in storage must be the same as added.", secret, s.get)
    assertEquals("Storage must contain 1 secret.", 1, sidechainSecretStorage.getAll.size)


    // Test 2: try to add single Secret, which is already present in Storage.
    assertTrue("Operation must be unsuccessful.", sidechainSecretStorage.add(secret).isFailure)


    // Test 3: try to add a list of unique Secrets
    res = sidechainSecretStorage.add(secretList)
    assertTrue("Add operation must be successful, instead failure occurred:\n %s".format(if (res.isFailure) res.failed.get.getMessage else ""), res.isSuccess)
    // verify
    assertEquals("Storage must contain 4 secrets.", 4, sidechainSecretStorage.getAll.size)
    assertEquals("Storage must contain all added keys.", secretList.size, sidechainSecretStorage.get(secretList.map(_.publicImage())).size)
    for (s <- sidechainSecretStorage.get(secretList.map(_.publicImage()))) {
      assertNotEquals("Storage must contain added key.", -1, secretList.indexOf(s))
    }


    // Test 4: try to add a list of Secrets, which are already present in Storage.
    assertTrue("Add operation must be unsuccessful.", sidechainSecretStorage.add(secretList).isFailure)


    // Test 5: remove Secret, which exists in the Storage
    res = sidechainSecretStorage.remove(secret.publicImage())
    assertTrue("Remove operation must be successful, instead failure occurred:\n %s".format(if (res.isFailure) res.failed.get.getMessage else ""), res.isSuccess)
    assertTrue("Storage must not contain Secret after remove operation.", sidechainSecretStorage.get(secret.publicImage()).isEmpty)
    assertEquals("Count of keys in storage must be - " + secretList.size, secretList.size, sidechainSecretStorage.getAll.size)


    // Test 6: remove list of Secrets, which exist in the Storage
    res = sidechainSecretStorage.remove(secretList.map(_.publicImage()))
    assertTrue("Remove operation must be successful, instead failure occurred:\n %s".format(if (res.isFailure) res.failed.get.getMessage else ""), res.isSuccess)
    assertEquals("Storage must be empty.", 0, sidechainSecretStorage.getAll.size)


    // Test 7: try to add Secret again, which was present then removed from Storage.
    res = sidechainSecretStorage.add(secret)
    assertTrue("Add operation must be successful, instead failure occurred:\n %s".format(if (res.isFailure) res.failed.get.getMessage else ""), res.isSuccess)

    val s1 = sidechainSecretStorage.get(secret.publicImage())
    assertTrue("Storage must contain added Secret.", s1.isDefined)
    assertEquals("Secret in storage must be the same as added.", secret, s1.get)
    assertEquals("Storage must contain 1 secret.", 1, sidechainSecretStorage.getAll.size)


    // Test 8: try to add duplicate Secrets
    assertTrue("Add operation must be unsuccessful.", sidechainSecretStorage.add(List(secret, secret)).isFailure)


    // Test 9: try to remove duplicate Secrets
    assertTrue("Add operation must be unsuccessful.", sidechainSecretStorage.remove(List(secret.publicImage(), secret.publicImage())).isFailure)
  }

  @Test
  def testCustomTypes(): Unit = {
    val pathToDB = tempFile()
    val storage = getStorage(pathToDB)
    val ss1 = new SidechainSecretStorage(storage, sidechainSecretsCompanion)
    val customSecret = getCustomPrivateKey
    var exceptionThrown = false
    var ss2: SidechainSecretStorage = null
    var customSecret2: Option[Secret] = null


    // Test 1: add Custom Secret to storage using SidechainSecretsCompanion with CustomSecret serializer
    assertTrue("Add operation must be successful.", ss1.add(customSecret).isSuccess)

    // verify changes
    val s = ss1.get(customSecret.publicImage())
    assertTrue("Storage must contain added Secret.", s.isDefined)
    assertEquals("Secret in storage must be the same as added.", customSecret, s.get)
    assertEquals("Storage must contain 1 secret.", 1, ss1.getAll.size)

    // Test 2: try to add Custom Secret again. Failure expected.
    assertTrue("Operation must be unsuccessful.", ss1.add(customSecret).isFailure)
    storage.close()

    // Test 3: open the store again and try to create SidechainSecretStorage WITHOUT Custom Secret serializer support
    val storage2 = getStorage(pathToDB)
    try {
      ss2 = new SidechainSecretStorage(storage2, sidechainSecretsCompanionCore)
    } catch {
      case e: RuntimeException => exceptionThrown = true
    }
    storage2.close()


    // Test 4: open the store again and try to create SidechainSecretStorage WITH Custom Secret serializer support
    exceptionThrown = false

    try {
      ss2 = new SidechainSecretStorage(getStorage(pathToDB), sidechainSecretsCompanion)
      customSecret2 = ss2.get(customSecret.publicImage())
    } catch {
      case _: Throwable => exceptionThrown = true
    }

    assertFalse("Exception must not be thrown for custom secret type.", exceptionThrown)
    assertTrue("Storage must contain added Secret.", customSecret2.isDefined)
    assertEquals("Secret in storage must be the same as added.", customSecret, customSecret2.get)
  }

  @Test
  def testGetNonce(): Unit = {
    val mockedAdapter = mock[VersionedLevelDbStorageAdapter]
    val sidechainSecretStorage = new SidechainSecretStorage(mockedAdapter, sidechainSecretsCompanion)
    val salt = PrivateKey25519Creator.getInstance().salt()
    val nonceKey: ByteArrayWrapper = sidechainSecretStorage.getNonceKey(salt)
    val nonce: Int = 7
    val nonceByteArray = Ints.toByteArray(nonce)
    Mockito.when(mockedAdapter.get(nonceKey)).thenReturn(Optional.of(new ByteArrayWrapper(nonceByteArray)))
    val result = sidechainSecretStorage.getNonce(salt)
    Assert.assertEquals(result.get, nonce)
  }

  @Test
  def testStoreSecretAndNonceAtomic(): Unit = {
    val pathToDB = tempFile()
    val storage = getStorage(pathToDB)
    val sidechainSecretStorage = new SidechainSecretStorage(storage, sidechainSecretsCompanion)

    // Test 1: add secret
    val salt = PrivateKey25519Creator.getInstance().salt()
    val nonce = 1
    val secret = getPrivateKey25519

    val result1 = sidechainSecretStorage.storeSecretAndNonceAtomic(secret, nonce, salt)

    val s1 = sidechainSecretStorage.get(secret.publicImage())
    assertTrue(result1.isSuccess)
    assertTrue("Storage must contain added Secret.", s1.isDefined)
    assertEquals("Secret in storage must be the same as added.", secret, s1.get)
    assertEquals("Storage must contain 1 secret.", 1, sidechainSecretStorage.getAll.size)

    val n1 = sidechainSecretStorage.getNonce(salt)
    assertTrue("Storage must contain nonce.", n1.isDefined)
    assertEquals("Nonce in storage must be the same as added.", nonce, n1.get)

    // Test 2: add Custom Secret to storage using SidechainSecretsCompanion with CustomSecret serializer
    val salt2 = PrivateKey25519Creator.getInstance().salt()
    val nonce2 = 2
    val customSecret = getCustomPrivateKey

    val result2 = sidechainSecretStorage.storeSecretAndNonceAtomic(customSecret, nonce2, salt2)

    val s2 = sidechainSecretStorage.get(customSecret.publicImage())
    assertTrue(result2.isSuccess)
    assertTrue("Storage must contain added Secret.", s2.isDefined)
    assertEquals("Secret in storage must be the same as added.", customSecret, s2.get)
    assertEquals("Storage must contain 2 secrets.", 2, sidechainSecretStorage.getAll.size)

    val n2 = sidechainSecretStorage.getNonce(salt2)
    assertTrue("Storage must contain nonce.", n2.isDefined)
    assertEquals("Nonce in storage must be the same as added.", nonce2, n2.get)

    // Test 3: test with same nonce, different salt and secret
    val secretSchnorr = SchnorrKeyGenerator.getInstance().generateSecret("seed1".getBytes(StandardCharsets.UTF_8))
    val saltSchnorr = SchnorrKeyGenerator.getInstance().salt()

    val result3 = sidechainSecretStorage.storeSecretAndNonceAtomic(secretSchnorr, nonce2, saltSchnorr)

    val s3 = sidechainSecretStorage.get(secretSchnorr.publicImage())
    assertTrue(result3.isSuccess)
    assertTrue("Storage must contain added Secret.", s3.isDefined)
    assertEquals("Secret in storage must be the same as added.", secretSchnorr, s3.get)
    assertEquals("Storage must contain 3 secrets.", 3, sidechainSecretStorage.getAll.size)

    val n3 = sidechainSecretStorage.getNonce(saltSchnorr)
    assertTrue("Storage must contain nonce.", n3.isDefined)
    assertEquals("Nonce in storage must be the same as added.", nonce2, n3.get)

    // Test 4: try to add same secret again
    val result4 = sidechainSecretStorage.storeSecretAndNonceAtomic(customSecret, nonce2, salt2)
    assertTrue(result4.isFailure)

    // Test 5: try to add negative nonce
    val result5 = sidechainSecretStorage.storeSecretAndNonceAtomic(getPrivateKey25519, -3, salt)
    assertTrue(result5.isFailure)

    // Test 6: try to add null salt
    val result6 = sidechainSecretStorage.storeSecretAndNonceAtomic(getPrivateKey25519, nonce, null)
    assertTrue(result6.isFailure)

    // Test 7: try to add null secret
    val result7 = sidechainSecretStorage.storeSecretAndNonceAtomic(null, nonce, salt)
    assertTrue(result7.isFailure)
  }
}

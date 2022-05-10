package com.horizen.storage

import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.customtypes.{CustomPrivateKey, CustomPrivateKeySerializer}
import com.horizen.fixtures._
import com.horizen.secret._
import com.horizen.utils.ByteArrayWrapper
import com.horizen.utils.Pair

import java.util.{HashMap => JHashMap, List => JList}
import java.lang.{Byte => JByte}
import com.horizen.SidechainTypes
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import org.mockito._
import scorex.crypto.hash.Blake2b256

import scala.util.Try

class SidechainSecretStorageTest
  extends JUnitSuite
  with SecretFixture
  with StoreFixture
  with MockitoSugar
  with SidechainTypes
{

  var mockedStorage: Storage = _
  var secretList = new ListBuffer[SidechainTypes#SCS]()
  var storedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  customSecretSerializers.put(CustomPrivateKey.SECRET_TYPE_ID, CustomPrivateKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
  val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)
  val sidechainSecretsCompanionCore = SidechainSecretsCompanion(new JHashMap())


  @Before
  def setUp() : Unit = {
    mockedStorage = mock[VersionedLevelDbStorageAdapter]
    secretList = new ListBuffer[Secret]()
    storedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

    secretList ++= getPrivateKey25519List(5).asScala ++ getCustomPrivateKeyList(5).asScala

    for (s <- secretList) {
      storedList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(s.publicImage().bytes))
        val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))
        new Pair(key,value)
      })
    }

    Mockito.when(mockedStorage.getAll).thenReturn(storedList.asJava)
    //Mockito.when(mockedStorage.getAll).thenThrow(new Exception("Storage is not initialized."))

    Mockito.when(mockedStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedList.filter(p => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })
  }

  @Test
  def testGet() : Unit = {
    val secretStorage = new SidechainSecretStorage(mockedStorage, sidechainSecretsCompanion)

    // Test 1: get one item
    assertEquals("Storage must return existing Secret.", secretList(3), secretStorage.get(secretList(3).publicImage()).get)


    // Test2: get multiple items
    val subList = secretList.slice(0, 2)
    assertTrue("Storage must contain specified Secrets.", secretStorage.get(subList.map(_.publicImage()).toList).asJava.containsAll(subList.asJava))


    // Test3: get all items
    assertTrue("Storage must contain all Secrets.", secretStorage.getAll.asJava.containsAll(secretList.asJava))


    // Test 4: try get non-existing item
    val nonExistingSecret = getPrivateKey25519("test non-existing".getBytes())
    assertEquals("Storage should NOT contain requested Secrets.", None, secretStorage.get(nonExistingSecret.publicImage()))


    // Test 5: get multiple items, not all of them exist
    assertEquals("Storage should contain NOT ALL requested WalletBoxes.", List(secretList.head),
      secretStorage.get(List(secretList.head.publicImage(), nonExistingSecret.publicImage())))
  }

  @Test
  def testAdd(): Unit = {
    val secretStorage = new SidechainSecretStorage(mockedStorage, sidechainSecretsCompanion)

    var tryRes: Try[SidechainSecretStorage] = null
    val expectedException = new IllegalArgumentException("on add exception")

    val newSecret = getPrivateKey25519("new secret".getBytes())
    val key = new ByteArrayWrapper(Blake2b256.hash(newSecret.publicImage().bytes))
    val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(newSecret))


    val toUpdate = java.util.Arrays.asList(new Pair(key,value))
    val toRemove = java.util.Arrays.asList()

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]
      assertEquals("WalletBoxStorage.update(...) actual toUpdate list is wrong.", toUpdate, actualToUpdate)
      assertEquals("WalletBoxStorage.update(...) actual toRemove list is wrong.", toRemove, actualToRemove)
      })
      // For Test 2:
      .thenAnswer(answer => throw expectedException)


    // Test 1: test successful add(...)
    tryRes = secretStorage.add(newSecret)
    assertTrue("SecretStorage successful adding expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)
    assertEquals("SecretStorage successful adding expected. Secret should be added.", newSecret, secretStorage.get(newSecret.publicImage()).get)


    // Test 2: test failed add(...), when Storage throws an exception
    val newSecret2 = getPrivateKey25519("new secret2".getBytes())
    tryRes = secretStorage.add(newSecret2)
    assertTrue("SecretStorage failure expected during add.", tryRes.isFailure)
    assertEquals("SecretStorage different exception expected during add.", expectedException, tryRes.failed.get)
    assertTrue("Storage should NOT contain Secret that was tried to update.", secretStorage.get(newSecret2.publicImage()).isEmpty)


    // Test 3: test failed add(...), when try to add existing Secret
    tryRes = secretStorage.add(secretList.head)
    assertTrue("SecretStorage failure expected during add.", tryRes.isFailure)
  }

  @Test
  def testRemove(): Unit = {
    val secretStorage = new SidechainSecretStorage(mockedStorage, sidechainSecretsCompanion)

    var tryRes: Try[SidechainSecretStorage] = null
    var expectedException = new IllegalArgumentException("on remove exception")

    val existingSecret = secretList.head
    val existingSecretKey = storedList.head.getKey


    val toUpdate = java.util.Arrays.asList()
    val toRemove = java.util.Arrays.asList(existingSecretKey)

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]
      assertEquals("WalletBoxStorage.update(...) actual toUpdate list is wrong.", toUpdate, actualToUpdate)
      assertEquals("WalletBoxStorage.update(...) actual toRemove list is wrong.", toRemove, actualToRemove)
      })
      // For Test 2:
      .thenAnswer(answer => throw expectedException)
      // For Test 3:
      .thenAnswer(answer => true)


    // Test 1: test successful remove(...)
    tryRes = secretStorage.remove(existingSecret.publicImage())

    assertTrue("SecretStorage successful removing expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)
    assertEquals("SecretStorage successful removing expected. Secret should be removed.", None, secretStorage.get(existingSecret.publicImage()))


    // Test 2: test failed remove(...) existing Secret, when Storage throws an exception
    tryRes = secretStorage.remove(secretList.head.publicImage())

    assertTrue("SecretStorage failure expected during remove.", tryRes.isFailure)
    assertEquals("SecretStorage different exception expected during remove.", expectedException, tryRes.failed.get)
    assertTrue("Storage should contain Secret that was tried to remove.", secretStorage.get(secretList.head.publicImage()).isEmpty)


    // Test 3: test successful remove(...), when try to remove non-existing Secret
    val newSecret = getPrivateKey25519("new secret".getBytes())
    tryRes = secretStorage.remove(newSecret.publicImage())

    assertTrue("SecretStorage successful removing expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)
    assertEquals("SecretStorage successful removing expected. Secret should be removed.", None, secretStorage.get(newSecret.publicImage()))
  }

}

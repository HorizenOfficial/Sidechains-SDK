package io.horizen.account.wallet

import io.horizen.SidechainTypes
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.customtypes._
import io.horizen.fixtures._
import io.horizen.secret._
import io.horizen.storage._
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, Pair}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.VersionTag
import sparkz.crypto.hash.Blake2b256
import java.lang.{Byte => JByte}
import java.nio.charset.StandardCharsets
import java.util
import java.util.{HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Random, Success, Try}

class AccountWalletTest
  extends JUnitSuite
    with SidechainRelatedMainchainOutputFixture
    with StoreFixture
    with MockitoSugar
{
  val mockedSecretStorage: Storage = mock[Storage]

  val secretList = new ListBuffer[Secret]()
  val storedSecretList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
  val secretVersions = new ListBuffer[ByteArrayWrapper]()

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  customSecretSerializers.put(CustomPrivateKey.SECRET_TYPE_ID, CustomPrivateKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
  val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)

  @Before
  def setUp() : Unit = {

    // Set base Secrets data
    secretList ++= getPrivateKey25519List(5).asScala
    secretVersions += getVersion

    for (s <- secretList) {
      storedSecretList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(s.publicImage().bytes))
        val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))
        new Pair(key, value)
      })
    }


    // Mock get and update methods of SecretStorage
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storedSecretList.asJava)

    Mockito.when(mockedSecretStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedSecretList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedSecretStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedSecretList.filter(p => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

    Mockito.when(mockedSecretStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
        ArgumentMatchers.anyList[Pair[ByteArrayWrapper,ByteArrayWrapper]](),
        ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        secretVersions.append(answer.getArgument(0))
        for (s <- answer.getArgument(2).asInstanceOf[JList[ByteArrayWrapper]].asScala) {
          val index = storedSecretList.indexWhere(p => p.getKey.equals(s))
          if (index != -1)
            storedSecretList.remove(index)
        }
        for (s <- answer.getArgument(1).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala) {
          val index = storedSecretList.indexWhere(p => p.getKey.equals(s.getKey))
          if (index != -1)
            storedSecretList.remove(index)
        }
        storedSecretList.appendAll(answer.getArgument(1).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala)
      })
  }

  @Test
  def testSecrets(): Unit = {
    val mockedSecretStorage1: SidechainSecretStorage = mock[SidechainSecretStorage]
    val accountWallet = new AccountWallet(
      "seed".getBytes(StandardCharsets.UTF_8),
      mockedSecretStorage1)
    val secret1 = getPrivateKey25519("testSeed1".getBytes(StandardCharsets.UTF_8))
    val secret2 = getPrivateKey25519("testSeed2".getBytes(StandardCharsets.UTF_8))


    // Test 1: test secret(proposition) and secretByPublicKey(proposition)
    Mockito.when(mockedSecretStorage1.get(secret1.publicImage())).thenReturn(Some(secret1))

    var actualSecret = accountWallet.secret(secret1.publicImage()).get
    assertEquals("SidechainWallet failed to retrieve a proper Secret.", secret1, actualSecret)

    actualSecret = accountWallet.secretByPublicKey(secret1.publicImage()).get
    assertEquals("SidechainWallet failed to retrieve a proper Secret.", secret1, actualSecret)


    // Test 2: test secrets(), publicKeys(), allSecrets(), secretsOfType(type)
    Mockito.when(mockedSecretStorage1.getAll).thenReturn(List(secret1, secret2))

    val actualSecrets = accountWallet.secrets()
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", Set(secret1, secret2), actualSecrets)

    val actualPublicKeys = accountWallet.publicKeys()
    assertEquals("SidechainWallet failed to retrieve a proper Public Keys.", Set(secret1.publicImage(), secret2.publicImage()), actualPublicKeys)

    val actualSecretsJava = accountWallet.allSecrets()
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", util.Arrays.asList(secret1, secret2), actualSecretsJava)

    val actualPrivateKeysJava = accountWallet.secretsOfType(classOf[PrivateKey25519])
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", util.Arrays.asList(secret1, secret2), actualPrivateKeysJava)

    val actualCustomKeysJava = accountWallet.secretsOfType(classOf[CustomPrivateKey])
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", util.Arrays.asList(), actualCustomKeysJava)


    // Test 3: try to add new valid Secret
    var onAddSecretEvent: Boolean = false
    Mockito.when(mockedSecretStorage1.add(secret1)).thenReturn(Try{mockedSecretStorage1})

    var result: Boolean = accountWallet.addSecret(secret1).isSuccess
    assertTrue("SidechainWallet failed to add new Secret.", result)


    // Test 4: try to add null Secret
    onAddSecretEvent = false
    assertTrue("SidechainWallet failure expected during adding NULL Secret.", accountWallet.addSecret(null).isFailure)


    // Test 5: try to add some Secret, exception in SecretStorage occurred.
    // ApplicationWallet.onAddSecret() event NOT expected
    var expectedException = new IllegalArgumentException("on add exception")
    Mockito.when(mockedSecretStorage1.add(secret1)).thenReturn(Failure(expectedException))

    var failureResult = accountWallet.addSecret(secret1)
    assertTrue("SidechainWallet failure expected during adding new Secret.", failureResult.isFailure)
    assertEquals("SidechainWallet different exception expected during adding new Secret.", expectedException, failureResult.failed.get)


    // Test 6: try to remove valid Secret
    var onRemoveSecretEvent: Boolean = false
    Mockito.when(mockedSecretStorage1.remove(secret1.publicImage())).thenReturn(Try{mockedSecretStorage1})
    result = accountWallet.removeSecret(secret1.publicImage()).isSuccess
    assertTrue("SidechainWallet failed to remove Secret.", result)


    // Test 7: try to remove null Secret
    onRemoveSecretEvent = false
    assertTrue("SidechainWallet failure expected during removing NULL Secret.", accountWallet.removeSecret(null).isFailure)


    // Test 8: try to remove some Secret, exception in SecretStorage occurred.
    // ApplicationWallet.onRemoveSecret() event NOT expected
    onRemoveSecretEvent = false
    expectedException = new IllegalArgumentException("on remove exception")
    Mockito.when(mockedSecretStorage1.remove(secret1.publicImage())).thenReturn(Failure(expectedException))

    failureResult = accountWallet.removeSecret(secret1.publicImage())
    assertTrue("SidechainWallet failure expected during removing new Secret.", failureResult.isFailure)
    assertEquals("SidechainWallet different exception expected during removing new Secret.", expectedException, failureResult.failed.get)
  }

  @Test
  def testGenerateNextSecret(): Unit = {
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]

    val accountWallet = new AccountWallet(
      "seed".getBytes(StandardCharsets.UTF_8),
      mockedSecretStorage)

    val storageList = ListBuffer[Secret]()
    val secret1 = getPrivateKey25519("seed1".getBytes(StandardCharsets.UTF_8))
    val secret2 = getPrivateKey25519("seed2".getBytes(StandardCharsets.UTF_8))
    storageList += secret1
    storageList += secret2
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)
    Mockito.when(mockedSecretStorage.storeSecretAndNonceAtomic(ArgumentMatchers.any[Secret], ArgumentMatchers.anyInt(),
      ArgumentMatchers.any[Array[Byte]])).thenReturn(Success(mockedSecretStorage))

    Mockito.when(mockedSecretStorage.getNonce(ArgumentMatchers.any())).thenReturn(Some(2))
    val privateKey25519Creator = PrivateKey25519Creator.getInstance()

    val result3 = accountWallet.generateNextSecret(privateKey25519Creator)
    assertTrue("Generation of first key should be successful", result3.isSuccess)
    val secret3 = result3.get._2
    storageList += secret3
    storageList -= secret1

    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)
    val result4 = accountWallet.generateNextSecret(privateKey25519Creator)

    assertTrue("Generation of second key should be successful", result4.isSuccess)
    val secret4 = result4.get._2

    assertEquals("keys should not be the same", secret3, secret4)
  }

  @Test
  def testGenerateSecretsOfDifferentDomainsIndependent(): Unit = {
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    Mockito.when(mockedSecretStorage.storeSecretAndNonceAtomic(ArgumentMatchers.any[Secret], ArgumentMatchers.anyInt(),
      ArgumentMatchers.any[Array[Byte]])).thenReturn(Success(mockedSecretStorage))

    val key25519_1 = getPrivateKey25519("seed1".getBytes(StandardCharsets.UTF_8))
    val schnorrKey_1 = getSchnorrKey("seed2".getBytes(StandardCharsets.UTF_8))
    val key25519_2 = getPrivateKey25519("seed3".getBytes(StandardCharsets.UTF_8))
    val key25519_3 = getPrivateKey25519("seed4".getBytes(StandardCharsets.UTF_8))
    val schnorrKey_2 = getSchnorrKey("seed5".getBytes(StandardCharsets.UTF_8))
    val storageList = ListBuffer[Secret](key25519_1, schnorrKey_1, key25519_2, key25519_3, schnorrKey_2)

    val privateKey25519Creator = PrivateKey25519Creator.getInstance()
    val schnorrKeyCreator = SchnorrKeyGenerator.getInstance()
    var schnorrNonce = 1
    var key25519Nonce = 2

    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)

    Mockito.when(mockedSecretStorage.getNonce(ArgumentMatchers.any[Array[Byte]])).thenAnswer(answer =>
      if(answer.getArgument(0) == schnorrKeyCreator.salt()) {
        schnorrNonce += 1
        Some(schnorrNonce)
      } else if(answer.getArgument(0) == privateKey25519Creator.salt()) {
        key25519Nonce += 1
        Some(key25519Nonce)
      }
    )

    val accountWallet = new AccountWallet(
      "seed".getBytes(StandardCharsets.UTF_8),
      mockedSecretStorage)


    val schnorrKey_3 = accountWallet.generateNextSecret(schnorrKeyCreator).get._2
    storageList += schnorrKey_3
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)

    val key25519_4 = accountWallet.generateNextSecret(privateKey25519Creator).get._2
    storageList += key25519_4
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)

    val schnorrKey_4 = accountWallet.generateNextSecret(schnorrKeyCreator).get._2
    storageList += schnorrKey_4
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)

    val key25519_5 = accountWallet.generateNextSecret(privateKey25519Creator).get._2
    storageList += key25519_5
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storageList.toList)

    Assert.assertEquals("Total number of secrets must be 9", 9, storageList.size)
    Assert.assertEquals("After generating schnorr secrets, schnorr nonce must be 3", 3, schnorrNonce)
    Assert.assertEquals("After generating 25519 key secrets, 25519 key nonce must be 4", 4, key25519Nonce)
  }
}

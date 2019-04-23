package com.horizen.storage

import scorex.util.ModifierId
import scorex.core.{bytesToId, idToBytes}

import com.horizen.{SidechainWallet, WalletBox, WalletBoxSerializer}
import com.horizen.box._
import com.horizen.companion._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.proposition._
import com.horizen.utils.ByteArrayWrapper
import javafx.util.Pair
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.block.SidechainBlock
import com.horizen.secret.{PrivateKey25519, Secret, SecretSerializer}
import com.horizen.transaction.{BoxTransaction, RegularTransaction}
import org.junit.Assert._

import scala.collection.mutable.{ListBuffer, Map}
import org.junit._
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito._

import scala.collection.JavaConverters._
import org.mockito._
import org.mockito.stubbing._

import scala.util.Random

class SidechainWalletTest
  extends JUnitSuite
    with SecretFixture
    with BoxFixture
    with IODBStoreFixture
    with MockitoSugar
{
  var seed = new Array[Byte](32)

  val mockedBoxStorage : Storage = mock[IODBStoreAdapter]
  val mockedSecretStorage : Storage = mock[IODBStoreAdapter]

  val boxList = new ListBuffer[WalletBox]()
  val storedBoxList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
  val boxVersions = new ListBuffer[ByteArrayWrapper]()

  val secretList = new ListBuffer[Secret]()
  val storedSecretList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
  val secretVersions = new ListBuffer[ByteArrayWrapper]()

  val customBoxesSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(CustomBox.BOX_TYPE_ID -> CustomBoxSerializer.getSerializer)
  val sidechainBoxesCompanion = new SidechainBoxesCompanion(customBoxesSerializers)

  val customSecretSerializers: Map[Byte, SecretSerializer[_ <: Secret]] =
    Map(CustomPrivateKey.SECRET_TYPE_ID ->  CustomPrivateKeySerializer.getSerializer)
  val sidechainSecretsCompanion = new SidechainSecretsCompanion(customSecretSerializers)

  @Before
  def setUp() : Unit = {

    Random.nextBytes(seed)

    secretList ++= getSecretList(5).asScala
    secretVersions += getVersion

    for (s <- secretList) {
      storedSecretList.append({
        val key = new ByteArrayWrapper(s.publicImage().bytes)
        val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))
        new Pair(key,value)
      })
    }

    Mockito.when(mockedSecretStorage.getAll).thenReturn(storedSecretList.asJava)

    Mockito.when(mockedSecretStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer((answer) => {
        storedSecretList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedSecretStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer((answer) => {
        storedSecretList.filter((p) => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

    Mockito.when(mockedSecretStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
        ArgumentMatchers.anyList[ByteArrayWrapper](),
        ArgumentMatchers.anyList[Pair[ByteArrayWrapper,ByteArrayWrapper]]()))
      .thenAnswer((answer) => {
        secretVersions.append(answer.getArgument(0))
        for (s <- answer.getArgument(1).asInstanceOf[JList[ByteArrayWrapper]].asScala)
          storedSecretList.remove(storedSecretList.indexWhere((p) => p.getKey.equals(s)))
        for (s <- answer.getArgument(2).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala)
          storedSecretList.remove(storedSecretList.indexWhere((p) => p.getKey.equals(s.getKey)))
        storedSecretList.appendAll(answer.getArgument(2))
      })

    boxList ++= getWalletBoxList(getRegularBoxList(secretList.asJava)).asScala
    boxVersions += getVersion

    for (b <- boxList) {
      storedBoxList.append({
        val wbs = new WalletBoxSerializer(sidechainBoxesCompanion)
        val key = new ByteArrayWrapper(b.box.id())
        val value = new ByteArrayWrapper(wbs.toBytes(b))
        new Pair(key,value)
      })
    }

    Mockito.when(mockedBoxStorage.getAll).thenReturn(storedBoxList.asJava)

    Mockito.when(mockedBoxStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer((answer) => {
        storedBoxList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedBoxStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer((answer) => {
        storedBoxList.filter((p) => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

    Mockito.when(mockedBoxStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper,ByteArrayWrapper]]()))
      .thenAnswer((answer) => {
        boxVersions.append(answer.getArgument(0))
        for (s <- answer.getArgument(1).asInstanceOf[JList[ByteArrayWrapper]].asScala)
          storedBoxList.remove(storedBoxList.indexWhere((p) => p.getKey.equals(s)))
        for (s <- answer.getArgument(2).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala)
          storedBoxList.remove(storedBoxList.indexWhere((p) => p.getKey.equals(s.getKey)))
        storedBoxList.appendAll(answer.getArgument(2))
      })

  }

  @Test
  def testScanPersistent() : Unit = {
    val mockedBlock : SidechainBlock = mock[SidechainBlock]
    val blockId = Array[Byte](32)
    val from : JList[Pair[RegularBox, PrivateKey25519]] = new JArrayList()
    val to : JList[Pair[PublicKey25519Proposition, java.lang.Long]]= new JArrayList()

    Random.nextBytes(blockId)

    for (i <- 0 to 2)
      from.add(new Pair(boxList(i).box.asInstanceOf[RegularBox], secretList(i).asInstanceOf[PrivateKey25519]))

    for (i <- 0 to 2)
      to.add(new Pair(secretList(i).publicImage().asInstanceOf[PublicKey25519Proposition], 10L))

    val tx : RegularTransaction = RegularTransaction.create(from, to, 10L, 1547798549470L)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(Seq(tx.asInstanceOf[BoxTransaction[Proposition, Box[Proposition]]]))

    Mockito.when(mockedBlock.id)
      .thenReturn(bytesToId(blockId))

    val sidechainWallet = new SidechainWallet(seed, new SidechainWalletBoxStorage(mockedBoxStorage, sidechainBoxesCompanion),
      new SidechainSecretStorage(mockedSecretStorage, sidechainSecretsCompanion), new CustomApplicationWallet())

    sidechainWallet.scanPersistent(mockedBlock)

    val wbl = sidechainWallet.boxes()

    assertEquals("Wallet must contain specified count of WallectBoxes.", boxList.size, wbl.size)
    assertTrue("Wallet must contain all specified WalletBoxes.",
      wbl.asJavaCollection.containsAll(boxList.slice(3, 5).asJavaCollection))
    assertTrue("Wallet must contain all specified WalletBoxes.",
      wbl.map(_.box).asJavaCollection.containsAll(tx.newBoxes))

  }

  @Test
  def testSecrets() : Unit = {

    val sidechainWallet = new SidechainWallet(seed, new SidechainWalletBoxStorage(mockedBoxStorage, sidechainBoxesCompanion),
      new SidechainSecretStorage(mockedSecretStorage, sidechainSecretsCompanion), new CustomApplicationWallet())

    //TEST for - Wallet.secrets
    val sl = sidechainWallet.secrets()

    assertEquals("Wallet must contain specified count of Secrets.", secretList.size, sl.size)
    assertTrue("Wallet must contain all specified Secrets", sl.asJavaCollection.containsAll(secretList.asJavaCollection))

    //TEST for - Wallet.publicKeys
    val pkl = sidechainWallet.publicKeys()

    assertEquals("Wallet must contain specified count of public keys.", secretList.size, pkl.size)
    assertTrue("Wallet must contain public keys for all specified Secrets.",
      pkl.asJavaCollection.containsAll(secretList.map(_.publicImage()).asJavaCollection))

    //TEST for - Wallet.secret(publicImage)
    assertEquals("Wallet must contain specified Secret.", secretList(0), sidechainWallet.secret(secretList(0).publicImage()).get)

    //TEST for - Wallet.removeSecret
    sidechainWallet.removeSecret(secretList(0).publicImage())

    assertTrue("Wallet must not contain specified Secret.", sidechainWallet.secret(secretList(0).publicImage()).isEmpty)

    //TEST for - Wallet.addSecret
    val s = getCustomSecret()

    sidechainWallet.addSecret(s)

    assertEquals("Wallet must contain specified Secret.", s, sidechainWallet.secret(s.publicImage()).get)

    //TEST for - NodeWallet.secretsOfType
    assertTrue("Wallet must contain all Secrets of specified type.",
      sidechainWallet.secretsOfType(classOf[PrivateKey25519]).containsAll(secretList.slice(1, 5).asJavaCollection))
    assertTrue("Wallet must contain all Secrets of specified type.",
      sidechainWallet.secretsOfType(classOf[CustomPrivateKey]).contains(s))

    //TEST for - NodeWallet.allSecrets
    val sl1 = sidechainWallet.allSecrets()
    assertTrue("Wallet must contain all Secrets.", secretList.slice(1,5).+=(s).asJavaCollection.containsAll(sl1))
  }

  @Test
  def testWalletBoxes() : Unit = {

    val sidechainWallet = new SidechainWallet(seed, new SidechainWalletBoxStorage(mockedBoxStorage, sidechainBoxesCompanion),
      new SidechainSecretStorage(mockedSecretStorage, sidechainSecretsCompanion), new CustomApplicationWallet())

    //TEST for - Wallet.boxes
    val wbl = sidechainWallet.boxes()

    assertEquals("Wallet must contain specified count of WallectBoxes.", boxList.size, wbl.size)
    assertTrue("Wallet must contain all specified WalletBoxes.", wbl.asJavaCollection.containsAll(boxList.asJavaCollection))

    //TEST for - NodeWallet.allboxes
    var bl = sidechainWallet.allBoxes

    assertEquals("Wallet must contain specified count of Boxes.", boxList.size, bl.size)
    assertTrue("Wallet must contain all specified Boxes.", boxList.map(_.box).asJavaCollection.containsAll(bl))

    //TEST for - NodeWallet.allboxes(boxIdsToExclude)
    bl = sidechainWallet.allBoxes(boxList.slice(0, 1).map(_.box.id()).asJava)

    assertEquals("Wallet must contain specified count of Boxes.", boxList.size - 1, bl.size)
    assertTrue("Wallet must contain all specified Boxes.", boxList.slice(1, 5).map(_.box).asJavaCollection.containsAll(bl))

    //TEST for - NodeWallet.boxesOfType
    bl = sidechainWallet.boxesOfType(classOf[RegularBox])

    assertEquals("Wallet must contain specified count of Boxes.", boxList.size, bl.size)
    assertTrue("Wallet must contain all specified Boxes.", boxList.map(_.box).asJavaCollection.containsAll(bl))

    //TEST for - NodeWallet.boxesOfType(boxIdsToExclude)
    bl = sidechainWallet.boxesOfType(classOf[RegularBox], boxList.slice(0, 1).map(_.box.id()).asJava)

    assertEquals("Wallet must contain specified count of Boxes.", boxList.size - 1, bl.size)
    assertTrue("Wallet must contain all specified Boxes.", boxList.slice(1, 5).map(_.box).asJavaCollection.containsAll(bl))

    //TEST for - NodeWallet.boxesBalance
    assertEquals("", boxList.map(_.box.value()).sum, sidechainWallet.boxesBalance(classOf[RegularBox]))
  }

}

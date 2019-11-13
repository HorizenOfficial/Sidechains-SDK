package com.horizen

import java.util.{ArrayList => JArrayList, List => JList}

import javafx.util.{Pair => JPair}
import com.horizen.block.SidechainBlock
import com.horizen.box.RegularBox
import com.horizen.fixtures.{IODBStoreFixture, SecretFixture, TransactionFixture}
import com.horizen.params.MainNetParams
import com.horizen.proposition.{MCPublicKeyHash, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator, Secret}
import com.horizen.storage.SidechainStateStorage
import com.horizen.utils.{ByteArrayWrapper, WithdrawalEpochInfo}
import com.horizen.state.{ApplicationState, SidechainStateReader}
import com.horizen.transaction.{RegularTransaction, SidechainTransaction}
import org.junit._
import org.junit.Assert._
import org.scalatest._
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}
import scorex.core.{VersionTag, bytesToId, bytesToVersion, idToVersion}
import scorex.util.ModifierId

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Random, Success}
import scala.collection.immutable._


class SidechainStateTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with IODBStoreFixture
    with MockitoSugar
    with SidechainTypes
{

  val mockedBoxStorage : SidechainStateStorage = mock[SidechainStateStorage]
  val mockedApplicationState : ApplicationState = mock[ApplicationState]

  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val boxVersion = new ListBuffer[ByteArrayWrapper]()
  val transactionList = new ListBuffer[RegularTransaction]()

  val secretList = new ListBuffer[Secret]()

  val params = MainNetParams()
  val withdrawalEpochInfo = WithdrawalEpochInfo(0,0)

  def getRegularTransaction (outputsCount: Int) : RegularTransaction = {
    val from: JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[JPair[PublicKey25519Proposition, java.lang.Long]] = new JArrayList[JPair[PublicKey25519Proposition, java.lang.Long]]()
    val withdrawalRequests: JList[JPair[MCPublicKeyHash, java.lang.Long]] = new JArrayList[JPair[MCPublicKeyHash, java.lang.Long]]()
    var totalFrom = 0L


    for (b <- boxList) {
      from.add(new JPair(b.asInstanceOf[RegularBox],
        secretList.find(_.publicImage().equals(b.proposition())).get.asInstanceOf[PrivateKey25519]))
      totalFrom += b.value()
    }

    val minimumFee = 5L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(s <- getSecretList(outputsCount).asScala) {
    val value = maxTo / outputsCount
    to.add(new JPair(s.publicImage().asInstanceOf[PublicKey25519Proposition], value))
    totalTo += value
  }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, withdrawalRequests, fee, System.currentTimeMillis - Random.nextInt(10000))

  }

  @Before
  def setUp() : Unit = {

  }

  @Test
  def testStateless() : Unit = {
    var exceptionThrown = false

    // Set base Secrets data
    secretList.clear()
    secretList ++= getSecretList(5).asScala
    // Set base Box data
    boxList.clear()
    boxList ++= getRegularBoxList(secretList.asJava).asScala.toList
    boxVersion.clear()
    boxVersion += getVersion
    transactionList.clear()
    transactionList += getRegularTransaction(1)

    // Mock get and update methods of BoxStorage
    Mockito.when(mockedBoxStorage.lastVersionId).thenReturn(Some(boxVersion.last))

    Mockito.when(mockedBoxStorage.get(ArgumentMatchers.any[Array[Byte]]()))
      .thenAnswer(answer => {
        val boxId = answer.getArgument(0).asInstanceOf[Array[Byte]]
        boxList.find(_.id().sameElements(boxId))
      })

    val sidechainState : SidechainState = new SidechainState(mockedBoxStorage, params, bytesToVersion(boxVersion.last.data), mockedApplicationState)

    //Test get
    assertEquals("State must return existing box.",
      boxList.head, sidechainState.closedBox(boxList.head.id()).get)

    //Test getClosedBox
    assertEquals("",
      boxList.head, sidechainState.getClosedBox(boxList.head.id()).get)

    //Test semanticValidity
    val mockedTransaction = mock[SidechainTypes#SCBT]

    Mockito.when(mockedTransaction.semanticValidity())
      .thenReturn(true)
      .thenReturn(false)

    assertTrue("Call of semanticValidity must be successful.",
      sidechainState.semanticValidity(mockedTransaction).isSuccess)
    assertTrue("Call of semanticValidity must be unsuccessful.",
      sidechainState.semanticValidity(mockedTransaction).isFailure)

    //Test validate(Transaction)
    val tryValidate = sidechainState.validate(transactionList.head)
    assertTrue("Transaction validation must be successful.",
      tryValidate.isSuccess)

    //Test validate(Block)
    val mockedBlock = mock[SidechainBlock]

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.parentId)
      .thenReturn(bytesToId(boxVersion.last.data))
      .thenReturn(bytesToId(boxVersion.last.data))
      .thenReturn("00000000000000000000000000000000".asInstanceOf[ModifierId])

    Mockito.when(mockedApplicationState.validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[SidechainBlock]()))
      .thenAnswer(answer => {
        true
      })
      .thenReturn(false)

    val validateTry1 = sidechainState.validate(mockedBlock)
    assertTrue(s"Block validation must be successful. But result is - $validateTry1",
      validateTry1.isSuccess)

    val validateTry2 = sidechainState.validate(mockedBlock)
    assertTrue(s"Block validation must be unsuccessful.",
      validateTry2.isFailure)

    val validateTry3 = sidechainState.validate(mockedBlock)
    assertTrue(s"Block validation must be unsuccessful.",
      validateTry2.isFailure)

    //Test changes
    val changes = sidechainState.changes(mockedBlock)

    assertTrue("Extracting changes from block must be successful.",
      changes.isSuccess)

    for(b <- changes.get.toRemove) {
      assertFalse("Box to remove is not found in storage.",
        boxList.indexWhere(_.id().sameElements(b.boxId)) == -1)
    }

    assertTrue("Box to add must be same as in transaction.",
      transactionList.head.newBoxes().asScala.head.equals(changes.get.toAppend.head.box))

  }

  @Test
  def testApplyModifier(): Unit = {

    // Set base Secrets data
    secretList.clear()
    secretList ++= getSecretList(5).asScala
    // Set base Box data
    boxList.clear()
    boxList ++= getRegularBoxList(secretList.asJava).asScala.toList
    boxVersion.clear()
    boxVersion += getVersion
    transactionList.clear()
    transactionList += getRegularTransaction(1)

    // Mock get and update methods of BoxStorage
    Mockito.when(mockedBoxStorage.lastVersionId)
        .thenAnswer(answer => {Some(boxVersion.last)})

    Mockito.when(mockedBoxStorage.get(ArgumentMatchers.any[Array[Byte]]()))
      .thenAnswer(answer => {
        val boxId = answer.getArgument(0).asInstanceOf[Array[Byte]]
        boxList.find(_.id().sameElements(boxId))
      })

    Mockito.when(mockedBoxStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[WithdrawalEpochInfo](),
      ArgumentMatchers.any[Set[SidechainTypes#SCB]](),
      ArgumentMatchers.any[Set[Array[Byte]]]()))
      .thenAnswer( answer => {
        val version = answer.getArgument[ByteArrayWrapper](0)
        val withdrawalEpochInfo = answer.getArgument[WithdrawalEpochInfo](1)
        val boxToUpdate = answer.getArgument[Set[SidechainTypes#SCB]](2)
        val boxToRemove = answer.getArgument[Set[Array[Byte]]](3)

        boxVersion += version

        for (b <- boxToRemove ++ boxToUpdate.map(_.id())) {
          val i = boxList.indexWhere(_.id().sameElements(b))
          if (i != -1)
            boxList.remove(i)
        }

        boxList ++= boxToUpdate

        Success(mockedBoxStorage)
      })

    val mockedBlock = mock[SidechainBlock]

    Mockito.when(mockedBlock.id)
      .thenReturn({
        bytesToId(getVersion.data)
      })

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.parentId)
      .thenReturn(bytesToId(boxVersion.last.data))

    Mockito.when(mockedApplicationState.validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[SidechainBlock]()))
      .thenAnswer(answer => {
        true
      })

    Mockito.when(mockedApplicationState.onApplyChanges(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[Array[Byte]](),
      ArgumentMatchers.any[JList[SidechainTypes#SCB]](),
      ArgumentMatchers.any[JList[Array[Byte]]]()))
      .thenReturn(Success(mockedApplicationState))

    val sidechainState : SidechainState = new SidechainState(mockedBoxStorage, params, bytesToVersion(boxVersion.last.data), mockedApplicationState)

    val applyTry = sidechainState.applyModifier(mockedBlock)

    assertTrue("ApplyChanges for block must be successful.",
      applyTry.isSuccess)

    assertTrue("Box in state must be same as in transaction.",
      sidechainState.closedBox(transactionList.head.newBoxes().asScala.head.id()).isDefined)

  }

}

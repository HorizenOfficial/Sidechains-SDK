package com.horizen.integration

import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList, Optional => JOptional}

import com.horizen.block.SidechainBlock
import javafx.util.{Pair => JPair}

import scala.collection.JavaConverters._
import com.horizen.{SidechainState, SidechainTypes, WalletBoxSerializer}
import com.horizen.box.{Box, BoxSerializer, CertifierRightBox, RegularBox}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.customtypes.{CustomBox, CustomBoxSerializer}
import com.horizen.fixtures.{IODBStoreFixture, SecretFixture, TransactionFixture}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, Secret}
import com.horizen.state.DefaultApplicationState
import com.horizen.storage.{IODBStoreAdapter, SidechainStateStorage}
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.ByteArrayWrapper
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest._
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import scorex.core.{bytesToId, bytesToVersion}
import scorex.crypto.hash.Blake2b256

import scala.collection.mutable.{ListBuffer, Map}
import scala.util.{Random, Try}

class SidechainStateTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with IODBStoreFixture
    with MockitoSugar
    with SidechainTypes
{
  val stateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), SidechainBoxesCompanion(new JHashMap()))
  val applicationState = new DefaultApplicationState()

  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val boxVersion = getVersion
  val transactionList = new ListBuffer[RegularTransaction]()

  val secretList = new ListBuffer[Secret]()

  def getRegularTransaction (outputsCount: Int) : RegularTransaction = {
    val from: JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[JPair[PublicKey25519Proposition, java.lang.Long]] = new JArrayList[JPair[PublicKey25519Proposition, java.lang.Long]]()
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

    RegularTransaction.create(from, to, fee, System.currentTimeMillis - Random.nextInt(10000))

  }

  @Before
  def setUp() : Unit = {

    secretList.clear()
    secretList ++= getSecretList(5).asScala

    boxList.clear()
    boxList ++= getRegularBoxList(secretList.asJava).asScala.toList

    transactionList.clear()
    transactionList += getRegularTransaction(1)

    stateStorage.update(boxVersion, boxList.toSet, Set[Array[Byte]]())

  }

  @Test
  def test() : Unit = {
    val sidechainState : SidechainState = new SidechainState(stateStorage, bytesToVersion(boxVersion.data), applicationState)

    for (b <- boxList) {
      //Test get
      assertEquals("State must return existing box.",
        b, sidechainState.closedBox(b.id()).get)

      //Test getClosedBox
      assertEquals("",
        b, sidechainState.getClosedBox(b.id()).get)
    }

    //Test applyModifier
    val mockedBlock = mock[SidechainBlock]

    val newVersion = getVersion

    Mockito.when(mockedBlock.id)
      .thenReturn({
        bytesToId(newVersion.data)
      })

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.parentId)
      .thenAnswer(answer => bytesToId(boxVersion.data))

    val applyTry = sidechainState.applyModifier(mockedBlock)

    assertTrue("ApplyChanges for block must be successful.",
      applyTry.isSuccess)

    assertEquals(s"State storage version must be updated to $newVersion",
      newVersion, stateStorage.lastVersionId.get)

    assertEquals("Rollaback deth must be 2.",
      2, sidechainState.maxRollbackDepth)

    for (b <- transactionList.head.newBoxes().asScala) {
      assertTrue("Box in state after applyModifier must contain newBoxes from transaction.",
        sidechainState.closedBox(b.id()).isDefined)
    }

    for (b <- transactionList.head.unlockers().asScala.map(_.closedBoxId())) {
      assertTrue("Box in state after applyModifier must not contain unlocked boxes from transaction.",
        sidechainState.closedBox(b).isEmpty)
    }

    //Test rollback
    val rollbackTry = sidechainState.rollbackTo(bytesToVersion(boxVersion.data))

    assertTrue("Rollback must be successful.",
      rollbackTry.isSuccess)

    assertEquals(s"State storage version must be rolled back to $boxVersion",
      boxVersion, stateStorage.lastVersionId.get)

    assertEquals("Rollaback deth must be 1.",
      1, sidechainState.maxRollbackDepth)

    for (b <- transactionList.head.newBoxes().asScala) {
      assertTrue("Box in state after applyModifier must not contain newBoxes from transaction.",
        sidechainState.closedBox(b.id()).isEmpty)
    }

    for (b <- transactionList.head.unlockers().asScala.map(_.closedBoxId())) {
      assertTrue("Box in state after applyModifier must contain unlocked boxes from transaction.",
        sidechainState.closedBox(b).isDefined)
    }
  }

}

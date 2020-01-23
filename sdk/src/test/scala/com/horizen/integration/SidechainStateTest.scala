package com.horizen.integration

import java.io.{File => JFile}
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList}

import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.data.{BoxData, RegularBoxData}
import com.horizen.utils.{Pair => JPair}

import scala.collection.JavaConverters._
import com.horizen.{SidechainSettings, SidechainState, SidechainTypes}
import com.horizen.box.{RegularBox, WithdrawalRequestBox}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.customtypes.DefaultApplicationState
import com.horizen.fixtures.{IODBStoreFixture, SecretFixture, TransactionFixture}
import com.horizen.params.MainNetParams
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.storage.{IODBStoreAdapter, SidechainStateStorage}
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.WithdrawalEpochInfo
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.settings.ScorexSettings
import scorex.core.{bytesToId, bytesToVersion}
import com.horizen.consensus._

import scala.collection.mutable.ListBuffer
import scala.util.Random

class SidechainStateTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with IODBStoreFixture
    with MockitoSugar
    with SidechainTypes
{
  val tmpDir = tempDir()
  val scorexSettings = mock[ScorexSettings]
  val sidechainSettings = mock[SidechainSettings]
  val sidechainBoxesCompanion = SidechainBoxesCompanion(new JHashMap())
  val applicationState = new DefaultApplicationState()

  val stateDir = new JFile(s"${tmpDir.getAbsolutePath}/state")
  stateDir.mkdirs()
  val store = getStore(stateDir)

  val stateStorage = new SidechainStateStorage(new IODBStoreAdapter(store), sidechainBoxesCompanion)

  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val boxVersion = getVersion
  val transactionList = new ListBuffer[RegularTransaction]()

  val secretList = new ListBuffer[PrivateKey25519]()

  val params = MainNetParams()
  val withdrawalEpochInfo = WithdrawalEpochInfo(0,0)
  val consensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)
  val forgingStakesAmount: Long = 0

  def getRegularTransaction (outputsCount: Int) : RegularTransaction = {
    val from: JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()
    var totalFrom = 0L


    for (b <- boxList) {
      from.add(new JPair(b.asInstanceOf[RegularBox],
        secretList.find(_.publicImage().equals(b.proposition())).get.asInstanceOf[PrivateKey25519]))
      totalFrom += b.value()
    }

    val minimumFee = 5L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(s <- getPrivateKey25519List(outputsCount).asScala) {
      val value = maxTo / outputsCount
      to.add(new RegularBoxData(s.publicImage(), value))
      totalTo += value
    }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, fee, System.currentTimeMillis - Random.nextInt(10000))

  }

  @Before
  def setUp(): Unit = {

    secretList.clear()
    secretList ++= getPrivateKey25519List(5).asScala

    boxList.clear()
    boxList ++= getRegularBoxList(secretList.asJava).asScala.toList

    transactionList.clear()
    transactionList += getRegularTransaction(1)

    stateStorage.update(boxVersion, withdrawalEpochInfo, boxList.toSet, Set[Array[Byte]](), Set[WithdrawalRequestBox](), Seq[ForgingStakeInfo](), consensusEpoch)
  }

  @Test
  def test(): Unit = {

    Mockito.when(sidechainSettings.scorexSettings)
      .thenAnswer(answer => {
        scorexSettings
      })

    Mockito.when(scorexSettings.dataDir)
      .thenAnswer(answer => {
        tmpDir
      })

    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, params, applicationState).get

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

    Mockito.when(mockedBlock.timestamp)
      .thenReturn(params.sidechainGenesisBlockTimestamp + params.consensusSecondsInSlot)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.parentId)
      .thenAnswer(answer => bytesToId(boxVersion.data))

    Mockito.when(mockedBlock.mainchainBlocks)
      .thenAnswer(answer => Seq[MainchainBlockReference]())

    val applyTry = sidechainState.applyModifier(mockedBlock)
    applyTry.get
    assertTrue("ApplyChanges for block must be successful.",
      applyTry.isSuccess)

    assertEquals(s"State storage version must be updated to $newVersion",
      bytesToVersion(newVersion.data), applyTry.get.version)

    assertEquals("Rollback depth must be 2.",
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
      bytesToVersion(boxVersion.data), rollbackTry.get.version)

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

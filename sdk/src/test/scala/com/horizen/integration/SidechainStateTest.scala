package com.horizen.integration

import java.io.{File => JFile}
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList}

import com.horizen.block.{MainchainBlockReferenceData, SidechainBlock}
import com.horizen.box.data.{ForgerBoxData, NoncedBoxData, RegularBoxData}
import com.horizen.box.{ForgerBox, NoncedBox, RegularBox, WithdrawalRequestBox}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.consensus._
import com.horizen.customtypes.DefaultApplicationState
import com.horizen.fixtures.sidechainblock.generation.SidechainBlocksGenerator
import com.horizen.fixtures.{IODBStoreFixture, SecretFixture, TransactionFixture}
import com.horizen.params.MainNetParams
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.storage.{IODBStoreAdapter, SidechainStateStorage}
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.{ByteArrayWrapper, WithdrawalEpochInfo, Pair => JPair}
import com.horizen.{ClosedBoxesDummyMerkleTree, ClosedBoxesZendooMerkleTree, SidechainState, SidechainTypes, TempDirectoriesCreator}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.{bytesToId, bytesToVersion}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Random

class SidechainStateTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with IODBStoreFixture
    with MockitoSugar
    with SidechainTypes
    with TempDirectoriesCreator
{
  val sidechainBoxesCompanion: SidechainBoxesCompanion = SidechainBoxesCompanion(new JHashMap())
  val applicationState = new DefaultApplicationState()

  var stateStorage: SidechainStateStorage = _
  var initialVersion: ByteArrayWrapper = _

  var initialForgerBox: ForgerBox = _
  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val secretList = new ListBuffer[PrivateKey25519]()

  val statePath: String = createNewTempDirPath().getAbsolutePath
  val dbPath: String = createNewTempDirPath().getAbsolutePath
  val cachePath: String = createNewTempDirPath().getAbsolutePath
  val closedBoxesTree = new ClosedBoxesZendooMerkleTree(statePath, dbPath, cachePath)
  val params: MainNetParams = MainNetParams(closedBoxesMerkleTreeStatePath = statePath, closedBoxesMerkleTreeDbPath = dbPath, closedBoxesMerkleTreeCachePath = cachePath)

  val initialWithdrawalEpochInfo: WithdrawalEpochInfo = WithdrawalEpochInfo(1, 1)
  val initialConsensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)

  val (_, genesisBlock, blockGenerator, forgingData, _) = SidechainBlocksGenerator.startSidechain(1000L, 89L, params)


  def getRegularTransaction(regularOutputsCount: Int, forgerOutputsCount: Int): RegularTransaction = {
    val outputsCount = regularOutputsCount + forgerOutputsCount

    val from: JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[NoncedBoxData[_ <: Proposition, _ <: NoncedBox[_ <: Proposition]]] = new JArrayList()
    var totalFrom = 0L


    for (b <- boxList) {
      if(b.isInstanceOf[RegularBox]) {
        from.add(new JPair(b.asInstanceOf[RegularBox],
          secretList.find(_.publicImage().equals(b.proposition())).get))
        totalFrom += b.value()
      }
    }

    val minimumFee = 5L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(s <- getPrivateKey25519List(regularOutputsCount).asScala) {
      val value = maxTo / outputsCount
      to.add(new RegularBoxData(s.publicImage(), value))
      totalTo += value
    }

    for(s <- getPrivateKey25519List(forgerOutputsCount).asScala) {
      val value = maxTo / outputsCount
      to.add(new ForgerBoxData(s.publicImage(), value, s.publicImage(), getVRFPublicKey(totalTo)))
      totalTo += value
    }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, fee, System.currentTimeMillis - Random.nextInt(10000))
  }

  @Before
  def setUp(): Unit = {
    secretList.clear()
    secretList ++= getPrivateKey25519List(10).asScala

    boxList.clear()
    boxList ++= getRegularBoxList(secretList.asJava).asScala.toList

    initialForgerBox = getForgerBox(secretList.head.publicImage(), 100L, 100L, secretList.head.publicImage(), getVRFPublicKey(1L))
    val forgingStakesToAppendSeq = Seq[ForgingStakeInfo](ForgingStakeInfo(initialForgerBox.id(), initialForgerBox.value()))
    boxList += initialForgerBox

    // Init SidechainStateStorage with boxList
    val tmpDir = tempDir()
    val stateDir = new JFile(s"${tmpDir.getAbsolutePath}/state")
    stateDir.mkdirs()
    val store = getStore(stateDir)

    initialVersion = getVersion

    stateStorage = new SidechainStateStorage(new IODBStoreAdapter(store), sidechainBoxesCompanion)
    stateStorage.update(
      initialVersion,
      initialWithdrawalEpochInfo,
      boxList.toSet,
      Set(),
      Seq[WithdrawalRequestBox](),
      forgingStakesToAppendSeq,
      initialConsensusEpoch,
      None
    )
  }

  @Test
  def genesisStateLoadNonEmptyStorage(): Unit = {

    val statePath: String = createNewTempDirPath().getAbsolutePath
    val dbPath: String = createNewTempDirPath().getAbsolutePath
    val cachePath: String = createNewTempDirPath().getAbsolutePath
    val params: MainNetParams = MainNetParams(closedBoxesMerkleTreeStatePath = statePath, closedBoxesMerkleTreeDbPath = dbPath, closedBoxesMerkleTreeCachePath = cachePath)

    val sidechainState = SidechainState.createGenesisState(stateStorage, params, applicationState, genesisBlock)

    val expectedMessage = s"State storage is not empty!"
    assertEquals(sidechainState.failed.get.getMessage, expectedMessage)
  }

  @Test
  def genesisStateLoadNonEmptyClosedBoxTree(): Unit = {

    val statePath: String = createNewTempDirPath().getAbsolutePath
    val dbPath: String = createNewTempDirPath().getAbsolutePath
    val cachePath: String = createNewTempDirPath().getAbsolutePath
    val closedBoxesTree: ClosedBoxesZendooMerkleTree = new ClosedBoxesZendooMerkleTree(statePath, dbPath, cachePath)
    val localParams: MainNetParams = MainNetParams(closedBoxesMerkleTreeStatePath = statePath, closedBoxesMerkleTreeDbPath = dbPath, closedBoxesMerkleTreeCachePath = cachePath)


    // Init SidechainStateStorage with boxList
    val tmpDir = tempDir()
    val stateDir = new JFile(s"${tmpDir.getAbsolutePath}/state")
    stateDir.mkdirs()
    val store = getStore(stateDir)
    val emptyStateStorage = new SidechainStateStorage(new IODBStoreAdapter(store), sidechainBoxesCompanion)

    val sidechainState = SidechainState.createGenesisState(emptyStateStorage, localParams, applicationState, genesisBlock)

    val expectedMessage = s"ClosedBoxesZendooMerkleTree can't be created for paths: ${localParams.closedBoxesMerkleTreeStatePath}, ${localParams.closedBoxesMerkleTreeDbPath}, ${localParams.closedBoxesMerkleTreeCachePath}"
    assertEquals(sidechainState.failed.get.getMessage, expectedMessage)
  }


  @Test
  def createGenesisState(): Unit = {
    val statePath: String = createNewTempDirPath().getAbsolutePath
    val dbPath: String = createNewTempDirPath().getAbsolutePath
    val cachePath: String = createNewTempDirPath().getAbsolutePath
    val localParams: MainNetParams = MainNetParams(closedBoxesMerkleTreeStatePath = statePath, closedBoxesMerkleTreeDbPath = dbPath, closedBoxesMerkleTreeCachePath = cachePath)

    // Init SidechainStateStorage with boxList
    val tmpDir = tempDir()
    val stateDir = new JFile(s"${tmpDir.getAbsolutePath}/state")
    stateDir.mkdirs()
    val store = getStore(stateDir)
    val emptyStateStorage = new SidechainStateStorage(new IODBStoreAdapter(store), sidechainBoxesCompanion)

    val sidechainState = SidechainState.createGenesisState(emptyStateStorage, localParams, applicationState, genesisBlock)

    assertTrue(sidechainState.isSuccess)
  }


  @Test
  def closedBoxes(): Unit = {
    val sidechainState: SidechainState =
      new SidechainState(stateStorage, params, bytesToVersion(stateStorage.lastVersionId.get.data), applicationState, new ClosedBoxesDummyMerkleTree())//SidechainState.restoreState(stateStorage, params, applicationState).get

    /*closedBoxesTree.closeTree()
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, params, applicationState).get*/

    // Test that initial boxes list present in the State
    for (box <- boxList) {
      assertEquals("State must return existing box.",
        box, sidechainState.closedBox(box.id()).get)

      assertEquals("State contains different box for given key.",
        box, sidechainState.getClosedBox(box.id()).get)
    }
  }




  @Test
  def currentConsensusEpochInfo(): Unit = {
    /*closedBoxesTree.closeTree()
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, params, applicationState).get*/
    val sidechainState: SidechainState =
      new SidechainState(stateStorage, params, bytesToVersion(stateStorage.lastVersionId.get.data), applicationState, new ClosedBoxesDummyMerkleTree())//SidechainState.restoreState(stateStorage, params, applicationState).get

    // Test that initial currentConsensusEpochInfo is valid
    val(modId, consensusEpochInfo) = sidechainState.getCurrentConsensusEpochInfo
    assertEquals("Consensus epoch info modifier id should be different.", bytesToId(initialVersion.data), modId)
    assertEquals("Consensus epoch info epoch number should be different.", initialConsensusEpoch, consensusEpochInfo.epoch)
    assertEquals("Consensus epoch info stake ids merkle tree size should be different.", 1, consensusEpochInfo.forgersBoxIds.leaves().size())
    assertEquals("Consensus epoch info epoch total stake should be different.", initialForgerBox.value(), consensusEpochInfo.forgersStake)
  }

  @Test
  def applyModifier(): Unit = {
    val sidechainState: SidechainState = new SidechainState(stateStorage, params, bytesToVersion(stateStorage.lastVersionId.get.data), applicationState, new ClosedBoxesDummyMerkleTree())//SidechainState.restoreState(stateStorage, params, applicationState).get

    // Test applyModifier with a single RegularTransaction with regular and forger outputs
    val mockedBlock = mock[SidechainBlock]

    val transactionList = new ListBuffer[RegularTransaction]()
    transactionList.append(getRegularTransaction(2, 1))
    val forgerOutputsAmount = transactionList.head.newBoxes().asScala.filter(_.isInstanceOf[ForgerBox]).foldLeft(0L)(_ + _.value())

    val newVersion = getVersion

    Mockito.when(mockedBlock.id)
      .thenReturn(bytesToId(newVersion.data))

    Mockito.when(mockedBlock.timestamp)
      .thenReturn(params.sidechainGenesisBlockTimestamp + params.consensusSecondsInSlot)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.parentId)
      .thenAnswer(answer => bytesToId(initialVersion.data))

    Mockito.when(mockedBlock.mainchainBlockReferencesData)
      .thenAnswer(answer => Seq[MainchainBlockReferenceData]())

    Mockito.when(mockedBlock.withdrawalEpochCertificateOpt).thenReturn(None)

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


    // Test that currentConsensusEpochInfo was changed
    val(modId, consensusEpochInfo) = sidechainState.getCurrentConsensusEpochInfo
    assertEquals("Consensus epoch info modifier id should be different.", bytesToId(newVersion.data), modId)
    assertEquals("Consensus epoch info epoch number should be different.", 2, consensusEpochInfo.epoch)
    assertEquals("Consensus epoch info stake ids merkle tree size should be different.", 2, consensusEpochInfo.forgersBoxIds.leavesNumber)
    assertEquals("Consensus epoch info epoch total stake should be different.", initialForgerBox.value() + forgerOutputsAmount, consensusEpochInfo.forgersStake)


    // Test rollback
    val rollbackTry = sidechainState.rollbackTo(bytesToVersion(initialVersion.data), Seq(mockedBlock))

    assertTrue("Rollback must be successful.",
      rollbackTry.isSuccess)

    assertEquals(s"State storage version must be rolled back to $initialVersion",
      bytesToVersion(initialVersion.data), rollbackTry.get.version)

    assertEquals("Rollback depth must be 1.",
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

  @Test
  def checkClosedTree(): Unit = {

  }
}

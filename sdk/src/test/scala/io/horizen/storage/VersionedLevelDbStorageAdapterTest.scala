package io.horizen.storage

import akka.actor.ActorSystem
import akka.util.Timeout
import io.horizen.SidechainTypes
import io.horizen.account.block.AccountBlock
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fixtures.AccountBlockFixture
import io.horizen.account.fork.ConsensusParamsFork
import io.horizen.account.websocket.NodeViewHolderUtilMocks
import io.horizen.consensus.ConsensusParamsUtil
import io.horizen.fixtures._
import io.horizen.fork.{ForkConfigurator, ForkManager, OptionalSidechainFork, SidechainForkConsensusEpoch, SimpleForkConfigurator}
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.utils.{ByteArrayWrapper, Pair}
import org.junit.Assert._
import org.junit._
import org.junit.rules.TemporaryFolder
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import java.util
import scala.concurrent.duration._
import io.horizen.utils.{Pair => JPair, _}


class VersionedLevelDbStorageAdapterTest
  extends JUnitSuite
  with SecretFixture
  with StoreFixture
  with MockitoSugar
  with SidechainTypes
  with CompanionsFixture
{
  val utilMocks = new NodeViewHolderUtilMocks()
  implicit lazy val actorSystem: ActorSystem = ActorSystem("storage-actor-test")
  implicit val timeout: Timeout = 5 seconds
  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
  val genesisBlock: AccountBlock = AccountBlockFixture.generateAccountBlock(sidechainTransactionsCompanion)

  @Before
  def init(): Unit = {
    ForkManager.reset()
  }

  val _temporaryFolder = new TemporaryFolder()
  @Rule  def temporaryFolder = _temporaryFolder

  @Test
  def testMaxVersionToKeepWithNoForkConfigured(): Unit = {

    // Test that the default value of storageVersionToKeep in the internal Storages is 720 * 2 + 1 if no consensusParameterForks enabled
    val forkConfigurator = new SimpleForkConfigurator
    ForkManager.init(forkConfigurator, "regtest")

    ConsensusParamsUtil.setCurrentConsensusEpoch(0)
    val storagePath = temporaryFolder.newFolder("sidechainStateStorage")
    val storage = new VersionedLevelDbStorageAdapter(storagePath)
    assertEquals("By default the storage version to keep should be 720 * 2 + 1", storage.getStorageVersionToKeep, 720 * 2 + 1)
    storage.close()
  }

  @Test
  def testMaxVersionToKeepWithForkNotActive(): Unit = {

    // Test that the default value of storageVersionToKeep in the internal Storages is 720 * 2 + 1 if consensusParameterForks enabled but not reached the activation height
    val forkConfigurator = new CustomForkConfigurator
    ForkManager.init(forkConfigurator, "regtest")

    ConsensusParamsUtil.setCurrentConsensusEpoch(0)
    val storagePath = temporaryFolder.newFolder("sidechainStateStorage")
    val storage = new VersionedLevelDbStorageAdapter(storagePath)
    assertEquals("By default the storage version to keep should be 720 * 2 + 1", storage.getStorageVersionToKeep, 720 * 2 + 1)
    storage.close()
  }

  @Test
  def testMaxVersionToKeepWithForkActive(): Unit = {

    // Test that the default value of storageVersionToKeep in the internal Storages is 1000 * 2 + 1 if consensusParameterForks enabled and active
    val forkConfigurator = new CustomForkConfigurator
    ForkManager.init(forkConfigurator, "regtest")

    ConsensusParamsUtil.setCurrentConsensusEpoch(20)
    val storagePath = temporaryFolder.newFolder("sidechainStateStorage")
    val storage = new VersionedLevelDbStorageAdapter(storagePath)
    assertEquals("By default the storage version to keep should be 1000 * 2 + 1", storage.getStorageVersionToKeep, 1000 * 2 + 1)
    storage.close()
  }


  @Test
  def testMaxVersionToKeepWithForkActiveDuringExecution(): Unit = {

    // Test that the default value of storageVersionToKeep in the internal Storages is 1000 * 2 + 1 if consensusParameterForks enabled and active
    val forkConfigurator = new CustomForkConfigurator
    ForkManager.init(forkConfigurator, "regtest")

    ConsensusParamsUtil.setCurrentConsensusEpoch(19)
    val storagePath = temporaryFolder.newFolder("sidechainStateStorage")
    val storage = new VersionedLevelDbStorageAdapter(storagePath)
    assertEquals("By default the storage version to keep should be 720 * 2 + 1", storage.getStorageVersionToKeep, 720 * 2 + 1)

    ConsensusParamsUtil.setCurrentConsensusEpoch(20)
    val newVer = getRandomByteArrayWrapper(32)
    val toStorageBuffer = new java.util.LinkedList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    storage.update(newVer, toStorageBuffer, Seq().asJava)
    assertEquals("By default the storage version to keep should be 1000 * 2 + 1", storage.getStorageVersionToKeep, 1000 * 2 + 1)

    storage.close()
  }

  private def getRandomByteArrayWrapper(length: Int): ByteArrayWrapper = {
    val generatedData: Array[Byte] = new Array[Byte](length)
    scala.util.Random.nextBytes(generatedData)
    generatedData
  }

  class CustomForkConfigurator extends ForkConfigurator {
    /**
     * Mandatory for every sidechain to provide an epoch number here.
     */
    override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(10, 20, 0)

    override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {
      Seq(new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](SidechainForkConsensusEpoch(20, 20, 20), new ConsensusParamsFork(1000))).asJava
    }
  }
}

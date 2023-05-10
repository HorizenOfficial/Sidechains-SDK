package io.horizen.consensus

import io.horizen.fork.{ForkManager, ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.storage.InMemoryStorageAdapter
import io.horizen.utils._
import org.junit.{Before, Test}
import sparkz.util._
import org.junit.Assert._

import java.nio.charset.StandardCharsets
import scala.util.Random


class ConsensusDataStorageTest {

  @Before
  def init(): Unit = {
    val forkManagerUtil = new ForkManagerUtil()
    forkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
  }

  @Test
  def simpleTestBeforeFork(): Unit = {
    simpleTest(0)
  }

  @Test
  def simpleTestAfterFork(): Unit = {
    simpleTest(new SimpleForkConfigurator().getSidechainFork1.regtestEpochNumber + 1)
  }

  def simpleTest(epochNumber: Int): Unit = {
    val rnd = new Random(23)

    val storage = new ConsensusDataStorage(new InMemoryStorageAdapter())

    val stakeData: Map[ConsensusEpochId, StakeConsensusEpochInfo] = (1 to 100).map{ _ =>
      val id = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8))))
      val stakeInfo =
        StakeConsensusEpochInfo(Utils.doubleSHA256Hash(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8)).take(merkleTreeHashLen), rnd.nextLong())
      (id, stakeInfo)
    }.toMap

    stakeData.foreach{case (id, stake) => storage.addStakeConsensusEpochInfo(id, stake)}

    assertTrue(stakeData.forall{case (id, stake) => storage.getStakeConsensusEpochInfo(id).get == stake})
    assertTrue(stakeData.forall{case (id, _) =>
      val nonExistingId = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(id.getBytes(StandardCharsets.UTF_8))))
      storage.getStakeConsensusEpochInfo(nonExistingId).isEmpty
    })

    val nonceData: Map[ConsensusEpochId, NonceConsensusEpochInfo] = (1 to 100).map{ _ =>
      val id = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8))))
      val nonceBytes:Array[Byte] = new Array[Byte](ForkManager.getSidechainConsensusEpochFork(epochNumber).nonceLength)
      rnd.nextBytes(nonceBytes)
      val nonceInfo =
        NonceConsensusEpochInfo(byteArrayToConsensusNonce(nonceBytes))
      (id, nonceInfo)
    }.toMap

    nonceData.foreach{case (id, nonceInfo) => storage.addNonceConsensusEpochInfo(id, nonceInfo)}

    assertTrue(nonceData.forall{case (id, nonce) => storage.getNonceConsensusEpochInfo(id).get == nonce})
    assertTrue(nonceData.forall{case (id, _) =>
      val nonExistingId = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(id.getBytes(StandardCharsets.UTF_8))))
      storage.getNonceConsensusEpochInfo(nonExistingId).isEmpty
    })
  }
}

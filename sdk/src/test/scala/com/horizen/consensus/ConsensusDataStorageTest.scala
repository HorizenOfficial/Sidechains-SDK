package com.horizen.consensus

import com.horizen.storage.InMemoryStorageAdapter
import com.horizen.utils._
import org.junit.Test
import scorex.util._
import org.junit.Assert._

import scala.util.Random


class ConsensusDataStorageTest {

  @Test
  def simpleTest(): Unit = {
    val rnd = new Random(23)

    val storage = new ConsensusDataStorage(new InMemoryStorageAdapter())

    val stakeData: Map[ConsensusEpochId, StakeConsensusEpochInfo] = (1 to 100).map{ _ =>
      val id = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(rnd.nextLong().toString.getBytes)))
      val stakeInfo =
        StakeConsensusEpochInfo(Utils.doubleSHA256Hash(rnd.nextLong().toString.getBytes).take(merkleTreeHashLen), rnd.nextLong())
      (id, stakeInfo)
    }.toMap

    stakeData.foreach{case (id, stake) => storage.addStakeConsensusEpochInfo(id, stake)}

    assertTrue(stakeData.forall{case (id, stake) => storage.getStakeConsensusEpochInfo(id).get == stake})
    assertTrue(stakeData.forall{case (id, _) =>
      val nonExistingId = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(id.getBytes())))
      storage.getStakeConsensusEpochInfo(nonExistingId).isEmpty
    })

    val nonceData: Map[ConsensusEpochId, NonceConsensusEpochInfo] = (1 to 100).map{ _ =>
      val id = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(rnd.nextLong().toString.getBytes)))
      val nonceBytes:Array[Byte] = new Array[Byte](consensusNonceLength)
      rnd.nextBytes(nonceBytes)
      val nonceInfo =
        NonceConsensusEpochInfo(byteArrayToConsensusNonce(nonceBytes))
      (id, nonceInfo)
    }.toMap

    nonceData.foreach{case (id, nonceInfo) => storage.addNonceConsensusEpochInfo(id, nonceInfo)}

    assertTrue(nonceData.forall{case (id, nonce) => storage.getNonceConsensusEpochInfo(id).get == nonce})
    assertTrue(nonceData.forall{case (id, _) =>
      val nonExistingId = blockIdToEpochId(bytesToId(Utils.doubleSHA256Hash(id.getBytes())))
      storage.getNonceConsensusEpochInfo(nonExistingId).isEmpty
    })
  }
}
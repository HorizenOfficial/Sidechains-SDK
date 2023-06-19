package io.horizen.consensus

import org.junit.Assert.fail
import org.junit.Test

import scala.util.{Failure, Success, Try}

class ConsensusPackageTest {

  @Test
  def testBuildVrfMessagePreFork(): Unit = {
    buildVrfMessage(ConsensusSlotNumber @@ 1, NonceConsensusEpochInfo(ConsensusNonce @@ Array[Byte](1,2,3,4,5,6,7,8)))
  }


  @Test
  def testBuildVrfMessageAfterFork(): Unit = {
      (Byte.MinValue to Byte.MaxValue).foreach { i =>
        Try {
          val nonce = NonceConsensusEpochInfo(ConsensusNonce @@ Array[Byte](-25, 116, 45, -58, -22, 40, 96, -17, 23, -45, 63, 102, -10, -33, 20, 81, -27, -117, -10, 107, 0, -108, -77, -55, -54, 10, 62, 35, 99, -128, -128, i.toByte))
          buildVrfMessage(ConsensusSlotNumber @@ 1, nonce)
        } match {
          case Success(_) =>
          case Failure(exception) => fail(s"$i has failed with ${exception.getMessage}")
        }
      }
  }
}

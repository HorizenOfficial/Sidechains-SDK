package io.horizen.forger

import com.google.common.primitives.Longs
import io.horizen.consensus._
import io.horizen.secret.VrfKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.{Ignore, Test}
import org.scalatestplus.junit.JUnitSuite

import java.nio.charset.StandardCharsets
import java.util.Random
import scala.collection.mutable

class ForgerGenerationRateTest extends JUnitSuite {

  @Ignore
  @Test
  def singleForgerFullStakeTest(): Unit = {
    val slotNumber = 1000
    val totalStake = 400
    val stake = totalStake

    // Generation VRF secret
    val rnd: Random = new Random()
    val vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8))

    // Construction message to sign: (slot + random(8 bytes))
    val initialNonce: Array[Byte] = new Array[Byte](8)
    rnd.nextBytes(initialNonce)
    val changedNonceBytes: Long = Longs.fromByteArray(initialNonce.take(Longs.BYTES))
    val nonce = Longs.toByteArray(changedNonceBytes)
    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ nonce)

    val stakes = (1 to slotNumber).map(slotNumber => {
      val slot = intToConsensusSlotNumber(slotNumber)
      val vrfMessage = buildVrfMessage(slot, consensusNonce)

      // vrfSecret.prove(vrfMessage)
      val vrfProofAndHash = vrfSecretKey.prove(vrfMessage)
      val vrfOutput = vrfProofAndHash.getValue

      // Check slot leadership
      vrfProofCheckAgainstStake(vrfOutput, stake, totalStake, stakePercentageFork = true)
    })

    assertEquals("Expected stakes result", slotNumber, stakes.count(s => s))
  }

  @Ignore
  @Test
  def singleForgerPartialStake(): Unit = {
    val slotNumber = 1000
    val totalStake = 500
    val stakeStep = 10
    val initialStake = 0

    // Generation VRF secret
    val rnd: Random = new Random()
    val vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8))

    // Construction message to sign: (slot + random(8 bytes))
    val initialNonce: Array[Byte] = new Array[Byte](8)
    rnd.nextBytes(initialNonce)
    val changedNonceBytes: Long = Longs.fromByteArray(initialNonce.take(Longs.BYTES))
    val nonce = Longs.toByteArray(changedNonceBytes)
    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ nonce)

    Seq.range(initialStake, totalStake, stakeStep).foreach(stake => {
      val slotRes = (1 to slotNumber).map(slotNumber => {
        val slot = intToConsensusSlotNumber(slotNumber)
        val vrfMessage = buildVrfMessage(slot, consensusNonce)

        // vrfSecret.prove(vrfMessage)
        val vrfProofAndHash = vrfSecretKey.prove(vrfMessage)
        val vrfOutput = vrfProofAndHash.getValue

        // Check slot leadership
        vrfProofCheckAgainstStake(vrfOutput, stake, totalStake, stakePercentageFork = true)
      })

      val slotsOccupied = slotRes.count(s => s)

      println("Stake %d of %d - Slots occupied %d of %d".format(stake, totalStake, slotsOccupied, slotNumber))
    })
  }

  @Ignore
  @Test
  def multipleForgersEqualStakes(): Unit = {
    val slotNumber = 10000
    val totalStake = 5000
    val forgerNum = 500
    val stake = totalStake / forgerNum

    // Generation VRF secret
    val rnd: Random = new Random()
    val forgerKeys = (1 to forgerNum).map(_ => VrfKeyGenerator.getInstance().generateSecret(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8)))

    // Construction message to sign: (slot + random(8 bytes))
    val initialNonce: Array[Byte] = new Array[Byte](8)
    rnd.nextBytes(initialNonce)
    val changedNonceBytes: Long = Longs.fromByteArray(initialNonce.take(Longs.BYTES))
    val nonce = Longs.toByteArray(changedNonceBytes)
    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ nonce)

    val stakes = (1 to slotNumber).map(slotNumber => {
      val slot = intToConsensusSlotNumber(slotNumber)
      val vrfMessage = buildVrfMessage(slot, consensusNonce)

      // vrfSecret.prove(vrfMessage)
      val forgerRes = forgerKeys.map(key => {
        val vrfProofAndHash = key.prove(vrfMessage)
        val vrfOutput = vrfProofAndHash.getValue

        // Check slot leadership
        vrfProofCheckAgainstStake(vrfOutput, stake, totalStake, stakePercentageFork = true)
      })

      println("Occupied forgers - %d".format(forgerRes.count(s => s)))
    })
  }

  @Ignore
  @Test
  def multipleForgersDifferentStakes(): Unit = {
    val slotNumber = 1000
    val forgerNum = 50
    val totalStake = 5000
    val stake = 2 * totalStake / (forgerNum * (forgerNum + 1))
    val regularStakeList: Seq[Int] = (1 to forgerNum - 1).map(i => i * stake)
    val stakeList = regularStakeList :+ (totalStake - regularStakeList.sum) // Put leftovers to the last stake

    // Generation VRF secret
    val rnd: Random = new Random()
    val forgers = stakeList.map(stake => (VrfKeyGenerator.getInstance().generateSecret(rnd.nextLong().toString.getBytes(StandardCharsets.UTF_8)), stake))
    val forgersGenRate: mutable.HashMap[Int, Int] = new mutable.HashMap[Int, Int]()
    stakeList.foreach(stake => forgersGenRate.put(stake, 0))

    // Construction message to sign: (slot + random(8 bytes))
    val initialNonce: Array[Byte] = new Array[Byte](8)
    rnd.nextBytes(initialNonce)
    val changedNonceBytes: Long = Longs.fromByteArray(initialNonce.take(Longs.BYTES))
    val nonce = Longs.toByteArray(changedNonceBytes)
    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ nonce)

    val stakes = (1 to slotNumber).map(slotNumber => {
      val slot = intToConsensusSlotNumber(slotNumber)
      val vrfMessage = buildVrfMessage(slot, consensusNonce)

      // vrfSecret.prove(vrfMessage)
      val forgerRes = forgers.map(forgerPair => {
        val vrfProofAndHash = forgerPair._1.prove(vrfMessage)
        val vrfOutput = vrfProofAndHash.getValue
        val forgerStake = forgerPair._2

        // Check slot leadership
        val proofRes = vrfProofCheckAgainstStake(vrfOutput, forgerStake, totalStake, stakePercentageFork = true)
        if (proofRes)
          forgersGenRate.put(forgerStake, forgersGenRate.get(forgerStake).get + 1)

        proofRes
      })

      println("Occupied forgers - %d".format(forgerRes.count(s => s)))
    })

    stakeList.foreach(stake => println("Forger stake/slot occupied - %d / %d".format(stake, forgersGenRate.get(stake).get)))
  }
}

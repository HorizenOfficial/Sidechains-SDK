package com.horizen

import java.math.BigInteger

import scorex.util.ModifierId
import supertagged.TaggedType

package object consensus {
  val rootHashLen: Int = 32
  val consensusHardcodedSaltString: String = "TEST"
  val stakePercentPrecision: BigInteger = BigInteger.valueOf(1000000) // where 1 / STAKE_PERCENT_PRECISION -- minimal possible stake percentage to be able to forge
  val stakePercentPrecisionAsDouble: Double = stakePercentPrecision.doubleValue()

  object ConsensusEpochNumber extends TaggedType[Int]
  type ConsensusEpochNumber = ConsensusEpochNumber.Type
  def intToConsensusEpochNumber(consensusEpochNumber: Int): ConsensusEpochNumber = ConsensusEpochNumber @@ consensusEpochNumber

  object ConsensusEpochId extends TaggedType[String]
  type ConsensusEpochId = ConsensusEpochId.Type
  def epochIdFromBlockId(blockId: ModifierId): ConsensusEpochId = ConsensusEpochId @@ blockId

  object ConsensusSlotNumber extends TaggedType[Int]
  type ConsensusSlotNumber = ConsensusSlotNumber.Type
  def intToConsensusSlotNumber(consensusSlotNumber: Int): ConsensusSlotNumber = ConsensusSlotNumber @@ consensusSlotNumber

  object ConsensusNonce extends TaggedType[Int]
  type ConsensusNonce = ConsensusNonce.Type
  def intToConsensusNonce(consensusNonce: Int): ConsensusNonce = ConsensusNonce @@ consensusNonce
  def powToConsensusNonce(powAsBigInteger: BigInteger): ConsensusNonce = intToConsensusNonce(powAsBigInteger.intValue())

  def buildVrfMessage(slotNumber: ConsensusSlotNumber, nonce: NonceConsensusEpochInfo): Array[Byte] = {
    //println(s"Build vrf message for slot number ${slotNumber}, nonce ${nonce.consensusNonce}")
    val slotNumberAsString = slotNumber.toString
    val nonceAsString = nonce.consensusNonce.toString

    (nonceAsString + slotNumberAsString + consensusHardcodedSaltString).getBytes
  }

  // @TODO shall be changed by adding "active slots coefficient" according to Ouroboros Praos Whitepaper (page 10)
  def hashToStakePercent(bytes: Array[Byte]): Double = {
    //@TODO check correctness!
    val hashAsNumber = new BigInteger(1, bytes)
    (Math.abs(hashAsNumber.remainder(stakePercentPrecision).doubleValue()) / stakePercentPrecisionAsDouble) / 10 //@TODO WILL BE CHANGED!!!
  }
}

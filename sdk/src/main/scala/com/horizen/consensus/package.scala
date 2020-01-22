package com.horizen

import java.math.BigInteger

import scorex.util.ModifierId
import supertagged.TaggedType

package object consensus {
  val rootHashLen: Int = 32
  val consensusHardcodedSaltString: String = "TEST"
  val stakePercentPrecision: BigInteger = BigInteger.valueOf(10000) // where 1 / STAKE_PERCENT_PRECISION -- minimal possible stake percentage to be able to forge

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


  def buildVrfMessage(slotNumber: ConsensusSlotNumber, nonce: ConsensusNonce): Array[Byte] = {
    val slotNumberAsString = slotNumber.toString
    val nonceAsString = nonce.toString

    (nonceAsString + slotNumberAsString + consensusHardcodedSaltString).getBytes
  }

  def hashToStakePercent(bytes: Array[Byte]): Double = {
    //@TODO check correctness!
    val hashAsNumber = new BigInteger(1, bytes)
    hashAsNumber.remainder(stakePercentPrecision).doubleValue()
  }
}

package com.horizen

import java.math.{BigDecimal, BigInteger, MathContext}

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.vrf.VrfOutput
import scorex.util.ModifierId
import supertagged.TaggedType

package object consensus {
  val merkleTreeHashLen: Int = 32
  val sha256HashLen: Int = 32
  val consensusNonceLength: Int = Longs.BYTES

  val consensusHardcodedSaltString: Array[Byte] = "TEST".getBytes()
  val forgerStakePercentPrecision: BigDecimal = BigDecimal.valueOf(1000000) // where 1 / forgerStakePercentPrecision -- minimal possible forger stake percentage to be able to forge
  val stakeConsensusDivideMathContext: MathContext = MathContext.DECIMAL128 //shall be used during dividing, otherwise ArithmeticException is thrown in case of irrational number as division result

  object ConsensusEpochNumber extends TaggedType[Int]
  type ConsensusEpochNumber = ConsensusEpochNumber.Type
  def intToConsensusEpochNumber(consensusEpochNumber: Int): ConsensusEpochNumber = ConsensusEpochNumber @@ consensusEpochNumber

  /**
   * Consensus epoch id is defined by last block in consensus epoch.
   * For example for chain A -> B -> C -> D -> E -> (end of consensus epoch), consensus epoch id is E.
   * It is possible to for that chain have consensus epoch with id D, in case if block E wasn't received but
   * consensus epoch shall be finished due time passing. In that case two different consensus epochs E and D are exist
   */
  object ConsensusEpochId extends TaggedType[String]
  type ConsensusEpochId = ConsensusEpochId.Type
  def blockIdToEpochId(blockId: ModifierId): ConsensusEpochId = ConsensusEpochId @@ blockId
  def lastBlockIdInEpochId(epochId: ConsensusEpochId): ModifierId = ModifierId @@ epochId.untag(ConsensusEpochId)

  object ConsensusSlotNumber extends TaggedType[Int]
  type ConsensusSlotNumber = ConsensusSlotNumber.Type
  def intToConsensusSlotNumber(consensusSlotNumber: Int): ConsensusSlotNumber = ConsensusSlotNumber @@ consensusSlotNumber

  //Slot number starting from genesis block
  object ConsensusAbsoluteSlotNumber extends TaggedType[Int]
  type ConsensusAbsoluteSlotNumber = ConsensusAbsoluteSlotNumber.Type
  def intToConsensusAbsoluteSlotNumber(consensusSlotNumber: Int): ConsensusAbsoluteSlotNumber = ConsensusAbsoluteSlotNumber @@ consensusSlotNumber


  object ConsensusNonce extends TaggedType[Array[Byte]]
  type ConsensusNonce = ConsensusNonce.Type
  def byteArrayToConsensusNonce(bytes: Array[Byte]): ConsensusNonce = ConsensusNonce @@ bytes

  object VrfMessage extends TaggedType[Array[Byte]]
  type VrfMessage = VrfMessage.Type

  def buildVrfMessage(slotNumber: ConsensusSlotNumber, nonce: NonceConsensusEpochInfo): VrfMessage = {
    val slotNumberBytes = Ints.toByteArray(slotNumber)
    val nonceBytes = nonce.consensusNonce

    val resBytes = Bytes.concat(slotNumberBytes, nonceBytes, consensusHardcodedSaltString)
    VrfMessage @@ resBytes
  }

  def vrfOutputToPositiveBigInteger(vrfOutput: VrfOutput): BigInteger = {
    new BigInteger(1, vrfOutput.bytes())
  }

  def vrfProofCheckAgainstStake(vrfOutput: VrfOutput, actualStake: Long, totalStake: Long): Boolean = {
    val requiredStakePercentage: BigDecimal = vrfOutputToRequiredStakePercentage(vrfOutput)
    val actualStakePercentage: BigDecimal = new BigDecimal(actualStake).divide(new BigDecimal(totalStake), stakeConsensusDivideMathContext)

    requiredStakePercentage.compareTo(actualStakePercentage) match {
      case -1 => true //required percentage is less than actual
      case  0 => true //required percentage is equal to actual
      case  _ => false //any other case
    }
  }

  // @TODO shall be changed by adding "active slots coefficient" according to Ouroboros Praos Whitepaper (page 10)
  def vrfOutputToRequiredStakePercentage(vrfOutput: VrfOutput): BigDecimal = {
    val hashAsBigDecimal: BigDecimal = new BigDecimal(vrfOutputToPositiveBigInteger(vrfOutput))

    hashAsBigDecimal
      .remainder(forgerStakePercentPrecision) //got random number from 0 to forgerStakePercentPrecision - 1
      .divide(forgerStakePercentPrecision, stakeConsensusDivideMathContext) //got random number from 0 to 0.(9)
  }
}

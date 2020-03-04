package com.horizen

import java.math.BigInteger

import com.horizen.vrf.VRFProof
import scorex.util.ModifierId
import supertagged.TaggedType

package object consensus {
  val merkleTreeHashLen: Int = 32
  val sha256HashLen: Int = 32

  val consensusHardcodedSaltString: String = "TEST" //do we need it? In original Ouroboros it was used for distinguish forging VRF and nonce VRF
  val stakePercentPrecision: BigInteger = BigInteger.valueOf(1000000) // where 1 / STAKE_PERCENT_PRECISION -- minimal possible stake percentage to be able to forge
  val stakePercentPrecisionAsDouble: Double = stakePercentPrecision.doubleValue()

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

  object ConsensusNonce extends TaggedType[Array[Byte]]
  type ConsensusNonce = ConsensusNonce.Type
  def bigIntToConsensusNonce(consensusNonce: BigInteger): ConsensusNonce = ConsensusNonce @@ consensusNonce.toByteArray

  def buildVrfMessage(slotNumber: ConsensusSlotNumber, nonce: NonceConsensusEpochInfo): Array[Byte] = {
    val slotNumberAsString = slotNumber.toString
    val nonceAsString = nonce.getAsStringForVrfBuilding

    val vrfString = nonceAsString + slotNumberAsString + consensusHardcodedSaltString
    vrfString.getBytes
  }

  def sha256HashToBigInteger(bytes: Array[Byte]): BigInteger = {
    require(bytes.length == sha256HashLen)
    new BigInteger(1, bytes)
  }

  def vrfProofCheckAgainstStake(actualStake: Long, vrfProof: VRFProof, totalStake: Long): Boolean = {
    val requiredPercentage: BigDecimal = BigDecimal(hashToStakePercent(vrfProof.proofToVRFHash()))
    val actualPercentage: BigDecimal = BigDecimal(actualStake) / totalStake

    requiredPercentage <= actualPercentage
  }

  // @TODO shall be changed by adding "active slots coefficient" according to Ouroboros Praos Whitepaper (page 10)
  def hashToStakePercent(bytes: Array[Byte]): Double = {
    //@TODO check correctness!
    val hashAsNumber: BigInteger = sha256HashToBigInteger(bytes)
    (Math.abs(hashAsNumber.remainder(stakePercentPrecision).doubleValue()) / stakePercentPrecisionAsDouble) / 10 //@TODO WILL BE CHANGED!!!
  }

  //Shall be SNARK friendly???
  def getMinimalHash(hashes: Iterable[Array[Byte]]): Option[BigInteger] = hashes.map(sha256HashToBigInteger).reduceOption(_ min _)
}

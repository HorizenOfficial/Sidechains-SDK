package com.horizen

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.FieldElementUtils
import com.horizen.poseidonnative.PoseidonHash
import com.horizen.vrf.VrfOutput
import scorex.util.ModifierId
import supertagged.TaggedType

import java.math.{BigDecimal, BigInteger, MathContext}
import java.nio.charset.StandardCharsets

package object consensus {
  val merkleTreeHashLen: Int = 32
  val sha256HashLen: Int = 32
  val consensusNonceAllowedLengths: Seq[Int] = Seq(8, 32)

  val consensusHardcodedSaltString: Array[Byte] = "TEST".getBytes(StandardCharsets.UTF_8)
  val consensusPreForkLength: Int = 4 + 8 + consensusHardcodedSaltString.length
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
    if (resBytes.length > consensusPreForkLength) {
      val nonceBytesHalves = nonceBytes.splitAt(nonceBytes.length / 2)
      VrfMessage @@ generateHashAndCleanUp(
        slotNumberBytes,
        nonceBytesHalves._1,
        nonceBytesHalves._2,
        consensusHardcodedSaltString
      )
    }
    else
      VrfMessage @@ resBytes
  }

  private def generateHashAndCleanUp(elements: Array[Byte]*): Array[Byte] = {
    val digest = PoseidonHash.getInstanceConstantLength(elements.length)
    elements.foreach { element =>
      val fieldElement = FieldElementUtils.elementToFieldElement(element)
      digest.update(fieldElement)
      fieldElement.freeFieldElement()
    }
    val hash = digest.finalizeHash()
    val result = hash.serializeFieldElement()
    digest.freePoseidonHash()
    hash.freeFieldElement()
    result
  }

  def vrfOutputToPositiveBigInteger(vrfOutput: VrfOutput): BigInteger = {
    new BigInteger(1, vrfOutput.bytes())
  }

  def vrfProofCheckAgainstStake(vrfOutput: VrfOutput, actualStake: Long, totalStake: Long, stakePercentageFork: Boolean): Boolean = {
    val requiredStakePercentage: BigDecimal = vrfOutputToRequiredStakePercentage(vrfOutput, stakePercentageFork)
    val actualStakePercentage: BigDecimal = new BigDecimal(actualStake).divide(new BigDecimal(totalStake), stakeConsensusDivideMathContext)

    requiredStakePercentage.compareTo(actualStakePercentage) match {
      case -1 => true //required percentage is less than actual
      case  0 => true //required percentage is equal to actual
      case  _ => false //any other case
    }
  }

  // @TODO shall be changed by adding "active slots coefficient" according to Ouroboros Praos Whitepaper (page 10)
  def vrfOutputToRequiredStakePercentage(vrfOutput: VrfOutput, stakePercentageFork: Boolean): BigDecimal = {
    val hashAsBigDecimal: BigDecimal = new BigDecimal(vrfOutputToPositiveBigInteger(vrfOutput))

    if (stakePercentageFork) {
      val maximumValue: BigDecimal = new BigDecimal(2).pow(CryptoLibProvider.vrfFunctions.vrfOutputLen * 8) // 2^256
      hashAsBigDecimal
        .divide(maximumValue, stakeConsensusDivideMathContext) //got random number from 0 to 0.(9)
        .max(new BigDecimal(1).divide(forgerStakePercentPrecision))
    }
    else
      hashAsBigDecimal
        .remainder(forgerStakePercentPrecision) //got random number from 0 to forgerStakePercentPrecision - 1
        .divide(forgerStakePercentPrecision, stakeConsensusDivideMathContext) //got random number from 0 to 0.(9)
  }
}

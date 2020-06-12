package com.horizen.fixtures.sidechainblock.generation

import java.util.Random

import com.horizen.fixtures.VrfGenerator
import com.horizen.params.NetworkParams
import com.horizen.proof.VrfProof

case class CorruptedGenerationRules(timestampShiftInSlots: Int = 0,
                                    getOtherSidechainForgingData: Boolean = false,
                                    merklePathFromPreviousEpoch: Boolean = false,
                                    consensusNonceShift: Long = 0,
                                    consensusSlotShift: Int = 0,
                                    stakeCheckCorruption: Boolean = false,
                                    forgerBoxCorruptionRules: Option[ForgerBoxCorruptionRules] = None,
                                    forcedVrfProof: Option[VrfProof] = None,
                                   ) {
  override def toString: String = {
    "CorruptedGenerationRules(" +
    s"timestampShiftInSlots = ${timestampShiftInSlots}, " +
    s"getOtherSidechainForgingData = ${getOtherSidechainForgingData}, " +
    s"merklePathFromPreviousEpoch = ${merklePathFromPreviousEpoch}, " +
    s"consensusNonceShift = ${consensusNonceShift}, " +
    s"consensusSlotShift = ${consensusSlotShift}, " +
    s"stakeCheckCorruptionCheck = ${stakeCheckCorruption} " +
    s"forgerBoxCorruptionRules = ${forgerBoxCorruptionRules})"
  }

  def getStakeCheckCorruptionFunction: Boolean => Boolean =
    if (stakeCheckCorruption) CorruptedGenerationRules.stakeInversion else CorruptedGenerationRules.stakeIdentity
}

object CorruptedGenerationRules {
  val emptyCorruptedGenerationRules: CorruptedGenerationRules = CorruptedGenerationRules()
  val stakeInversion: Boolean => Boolean = b => !b
  val stakeIdentity: Boolean => Boolean = b => b

  def corruptGenerationRules(rnd: Random, params: NetworkParams, generator: SidechainBlocksGenerator, initialRules: GenerationRules): GenerationRules = {
    initialRules.copy(corruption = CorruptedGenerationRules.generate(rnd, params))
  }

  def generate(rnd: Random, params: NetworkParams): CorruptedGenerationRules = {
    var generated = emptyCorruptedGenerationRules
    while (generated == emptyCorruptedGenerationRules) generated = generateIteration(rnd, params)

    generated
  }

  private def generateIteration(rnd: Random, params: NetworkParams): CorruptedGenerationRules = {
    var rule = CorruptedGenerationRules()
    /* @TODO add those checks. Implementation more complex logic is required: false positive test can occurs due forger is eligible for current and shifted slot
    if (rnd.nextInt(100) < 2) {
      rule = rule.copy(timestampShiftInSlots = rnd.nextInt() % (params.consensusSlotsInEpoch * 2))
    }

    if (rnd.nextInt(100) < 2) {
      rule = rule.copy(consensusSlotShift = rnd.nextInt() % (params.consensusSlotsInEpoch * 2))
    }

    if (rnd.nextInt(100) < 5) {
      val consensusSlotShift = 0 - Math.abs(rnd.nextInt() % params.consensusSlotsInEpoch * 2)
      val timestampShift = consensusSlotShift
      rule = rule.copy(consensusSlotShift = consensusSlotShift, timestampShiftInSlots = timestampShift)
    }*/

    if (rnd.nextInt(100) < 3) {
      rule = rule.copy(getOtherSidechainForgingData = true)
    }

    if (rnd.nextInt(100) < 4) {
      rule = rule.copy(merklePathFromPreviousEpoch = true)
    }

    if (rnd.nextInt(100) < 5) {
      rule = rule.copy(consensusNonceShift = rnd.nextLong())
    }

    if (rnd.nextInt(100) < 3) {
      rule = rule.copy(stakeCheckCorruption = true)
    }

    if (rnd.nextInt(100) < 5) {
      rule = rule.copy(forgerBoxCorruptionRules = Some(ForgerBoxCorruptionRules.generate(rnd, params)))
    }

    if (rnd.nextInt(100) < 3) {
      rule = rule.copy(forcedVrfProof = Some(VrfGenerator.generateProof(rnd.nextInt())))
    }

    rule
  }
}

case class ForgerBoxCorruptionRules(propositionChanged: Boolean = false,
                                    nonceShift: Long = 0,
                                    valueShift: Long = 0,
                                    blockSignPropositionChanged: Boolean = false,
                                    vrfPubKeyChanged: Boolean = false) {
  override def toString: String = {
    "ForgerBoxCorruptionRules(" +
    s"propositionChanged = ${propositionChanged}, " +
    s"nonceShift = ${nonceShift}, " +
    s"valueShift = ${valueShift}, " +
    s"blockSignPropositionChanged = ${blockSignPropositionChanged}, " +
    s"vrfPubKeyChanged = ${vrfPubKeyChanged}"
  }
}

object ForgerBoxCorruptionRules {
  val emptyCorruptedForgerBoxGenerationRules: ForgerBoxCorruptionRules = ForgerBoxCorruptionRules()

  def generate(rnd: Random, params: NetworkParams): ForgerBoxCorruptionRules = {
    var generated: ForgerBoxCorruptionRules = emptyCorruptedForgerBoxGenerationRules
    while (generated == emptyCorruptedForgerBoxGenerationRules) generated = generateIteration(rnd, params)

    generated
  }

  private def generateIteration(rnd: Random, params: NetworkParams): ForgerBoxCorruptionRules = {
    var rule = ForgerBoxCorruptionRules()
    if (rnd.nextInt(100) < 1) {
      rule = rule.copy(propositionChanged = true)
    }

    if (rnd.nextInt(100) < 2) {
      rule = rule.copy(nonceShift = rnd.nextInt())
    }

    if (rnd.nextInt(100) < 2) {
      rule = rule.copy(valueShift = rnd.nextInt())
    }

    if (rnd.nextInt(100) < 1) {
      rule = rule.copy(blockSignPropositionChanged = true)
    }

    if (rnd.nextInt(100) < 2) {
      rule = rule.copy(vrfPubKeyChanged = true)
    }

    rule
  }
}

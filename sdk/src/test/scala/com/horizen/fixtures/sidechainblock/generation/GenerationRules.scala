package com.horizen.fixtures.sidechainblock.generation

import java.util.Random

import scorex.util.ModifierId

case class GenerationRules(forgingBoxesToAdd: Set[SidechainForgingData] = Set(),
                           forgingBoxesToSpent: Set[SidechainForgingData] = Set(),
                           mcReferenceIsPresent: Option[Boolean] = None,
                           corruption: CorruptedGenerationRules = CorruptedGenerationRules.emptyCorruptedGenerationRules,
                           forcedParentId: Option[ModifierId] = None,
                           forcedTimestamp: Option[Long] = None
                         ) {
  def isCorrupted: Boolean = corruption == CorruptedGenerationRules.emptyCorruptedGenerationRules
}

object GenerationRules {
  def generateCorrectGenerationRules(rnd: Random, allNotSpentForgerData: Set[SidechainForgingData]): GenerationRules = {
    val addForgingData: Set[SidechainForgingData] =
      if (allNotSpentForgerData.size > 100) {
        Set(SidechainForgingData.generate(rnd, Math.abs(rnd.nextInt(1000000))))
      }
      else {
        Set(SidechainForgingData.generate(rnd, Math.abs(rnd.nextInt(1000000))), SidechainForgingData.generate(rnd, Math.abs(rnd.nextInt(1000000))))
      }

    val removedForgingData: Set[SidechainForgingData] =
      if (rnd.nextBoolean()) {
        Set(allNotSpentForgerData.toSeq(rnd.nextInt(allNotSpentForgerData.size)))
      }
      else {
        val deleteSize = if (allNotSpentForgerData.size > 100) 10 else 1
        allNotSpentForgerData.toSeq.sortBy(_.forgingStakeInfo.stakeAmount)(Ordering[Long]).take(deleteSize).toSet
      }

    require((removedForgingData -- allNotSpentForgerData).isEmpty)

    GenerationRules(forgingBoxesToAdd = addForgingData, forgingBoxesToSpent = removedForgingData)
  }
}

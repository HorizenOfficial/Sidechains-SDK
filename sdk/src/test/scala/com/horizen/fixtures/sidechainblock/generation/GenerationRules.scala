package com.horizen.fixtures.sidechainblock.generation

import java.util.Random

case class GenerationRules(forgingBoxesToAdd: Set[SidechainForgingData] = Set(),
                          forgingBoxesToSpent: Set[SidechainForgingData] = Set(),
                          mcReferenceIsPresent: Option[Boolean] = None,
                          corruption: CorruptedGenerationRules = CorruptedGenerationRules.emptyCorruptedGenerationRules
                         ) {
}

object GenerationRules {
  def generateCorrectGenerationRules(rnd: Random, generator: SidechainBlocksGenerator): GenerationRules = {

    val allNotSpentForgerData: Set[SidechainForgingData] = generator.getNotSpentBoxes
    val addForgingData: Set[SidechainForgingData] = Set(SidechainForgingData.generate(rnd.nextLong(), Math.abs(rnd.nextInt(1000000))))
    val removedForgingData: Set[SidechainForgingData] = if (rnd.nextBoolean()) {Set(allNotSpentForgerData.toSeq(rnd.nextInt(allNotSpentForgerData.size)))} else Set()
    require((removedForgingData -- allNotSpentForgerData).isEmpty)

    GenerationRules(forgingBoxesToAdd = addForgingData, forgingBoxesToSpent = removedForgingData)
  }
}

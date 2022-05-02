package com.horizen.consensus

import java.util.Random

import com.horizen.SidechainHistory
import com.horizen.fixtures.sidechainblock.generation._
import com.horizen.params.{NetworkParams, TestNetParams}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class HistoryConsensusCheckerTest extends JUnitSuite with HistoryConsensusChecker {

  def testWithSeed(testSeed: Int): Unit = {
    //val testSeed = 234
    val rnd: Random = new Random(testSeed)

    val initialParams = TestNetParams(consensusSlotsInEpoch = 10, sidechainGenesisBlockTimestamp = 1333344452L)
    val (params, genesisBlock, genesisGenerator, genesisForgingData, genesisEndEpochInfo) = SidechainBlocksGenerator.startSidechain(10000000000L, testSeed, initialParams)
    val history: SidechainHistory = createHistory(params, genesisBlock, genesisEndEpochInfo)
    val nonce = history.calculateNonceForEpoch(blockIdToEpochId(genesisBlock.id))
    val stake = genesisEndEpochInfo.stakeConsensusEpochInfo
    history.applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(stake, nonce))
    println(s"//////////////// Genesis epoch ${genesisBlock.id} had been ended ////////////////")

    val generators = mutable.IndexedSeq(genesisGenerator)

    (1 to 50)
      .foldLeft[(SidechainHistory, mutable.IndexedSeq[SidechainBlocksGenerator])]((history, generators)) { (acc, index) =>
        val currentHistory: SidechainHistory = acc._1
        val currentGenerators: mutable.IndexedSeq[SidechainBlocksGenerator] =  acc._2

        val nextGenerator: SidechainBlocksGenerator = generatorSelection(rnd, currentGenerators)
        val nextCorrectGenerationRules: GenerationRules = GenerationRules.generateCorrectGenerationRules(rnd, nextGenerator.getNotSpentBoxes)

        println("try to add incorrect block(s)")
        tryToAddIncorrectBlocks(params, currentHistory, nextGenerator, nextCorrectGenerationRules, rnd)
        println("try to add correct block")
        val correctRes = Try(generateBlock(nextCorrectGenerationRules, nextGenerator, history)) match {
          case Success((gens, generatedBlock)) =>
            val updatedHistory = historyUpdateShallBeSuccessful(currentHistory, generatedBlock)
            val updatedGenerators = currentGenerators ++ gens
            (updatedHistory, updatedGenerators)

          case Failure(ex: GenerationIsNoLongerPossible) =>
            println("Finishing block generation")
            return

          case Failure(ex) =>
            println("Error during block generation")
            throw ex
        }

        correctRes
      }
  }

  private def tryToAddIncorrectBlocks(params: NetworkParams,
                                      currentHistory: SidechainHistory,
                                      currentGenerator: SidechainBlocksGenerator,
                                      correctGenerationRules: GenerationRules,
                                      rnd: Random,
                                      incorrectBlocksCount: Int = 2): Unit = Try {
    (1 to incorrectBlocksCount)
      .foreach{ _ =>
        val incorrectGenerationRules: GenerationRules = CorruptedGenerationRules.corruptGenerationRules(rnd, params, currentGenerator, correctGenerationRules)
        //println(s"Generated corruption rules are: ${incorrectGenerationRules}")
        currentGenerator
          .tryToGenerateBlockForCurrentSlot(incorrectGenerationRules)
          .map(generationInfo => historyUpdateShallBeFailed(currentHistory,generationInfo.block, incorrectGenerationRules))
    }
  }

  @Test
  def testManySeeds(): Unit = {
    val seed = 9084

    (50 to 50).foreach{index =>
      println(s"SEED IS ${index}")
      testWithSeed(index + seed)
    }
  }

}

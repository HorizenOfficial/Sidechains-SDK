package com.horizen.validation

import java.math.BigInteger
import java.time.Instant
import java.util.Random

import com.horizen.SidechainHistory
import com.horizen.block.SidechainBlock
import com.horizen.consensus.HistoryConsensusChecker
import com.horizen.fixtures.VrfGenerator
import com.horizen.fixtures.sidechainblock.generation.{ForgerBoxCorruptionRules, GenerationRules, SidechainBlocksGenerator}
import com.horizen.params.TestNetParams
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.collection.mutable
import scala.util.Try

class ConsensusValidatorTest extends JUnitSuite with HistoryConsensusChecker {
  val rnd = new Random(42)


  private def createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots: Int, slotLengthInSeconds: Int, blocksCount: Int):
    (SidechainHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock]) = {
    var res: Option[(SidechainHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock])] =  None
    while (res.isEmpty) {
      res = Try(createHistoryWithBlocksNoForks(new Random(rnd.nextLong()), epochSizeInSlots, slotLengthInSeconds, blocksCount)).toOption
    }
    val generators = res.get._2
    (res.get._1, generators.take(generators.size - 1), res.get._3)
  }

  private def createHistoryWithBlocksNoForks(rnd: Random, epochSizeInSlots: Int, slotLengthInSeconds: Int, blocksCount: Int):
    (SidechainHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock]) = {
    val genesisTimestamp: Int = 1583987714

    val initialParams = TestNetParams(
      consensusSlotsInEpoch = epochSizeInSlots,
      consensusSecondsInSlot = slotLengthInSeconds,
      sidechainGenesisBlockTimestamp = genesisTimestamp)

    val (params, genesisBlock, genesisGenerator, genesisForgingData, genesisEndEpochInfo) = SidechainBlocksGenerator.startSidechain(1000000L, rnd.nextInt(), initialParams)

    var history: SidechainHistory = createHistory(params, genesisBlock, genesisEndEpochInfo)
    history.applyStakeConsensusEpochInfo(genesisBlock.id, genesisEndEpochInfo.stakeConsensusEpochInfo)
    println(s"//////////////// Genesis epoch ${genesisBlock.id} had been ended ////////////////")

    val generators = mutable.Buffer(genesisGenerator)
    val generatedBlocks = mutable.Buffer(genesisBlock)

    for (i <- 1 to blocksCount) {
      val lastGenerator = generators.last
      val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, lastGenerator.getNotSpentBoxes)
      val (gens, generatedBlock) = generateBlock(generationRules, lastGenerator, history)
      if (i != blocksCount) {
        history = historyUpdateShallBeSuccessful(history, generatedBlock)
        generatedBlocks.append(generatedBlock)
      }

      generators.appendAll(gens)
    }

    (history, generators, generatedBlocks)
  }

  @Test
  def nonGenesisBlockCheck(): Unit = {
    val epochSizeInSlots = 10
    val slotLengthInSeconds = 20
    val (history: SidechainHistory, generators: Seq[SidechainBlocksGenerator], blocks) = createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots, slotLengthInSeconds, epochSizeInSlots * 4)

    val lastGenerator = generators.last

    /////////// Timestamp related checks //////////////
    //check block in the future
    val blockInFuture = generateBlockInTheFuture(lastGenerator)
    history.append(blockInFuture).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == "Block had been generated in the future")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedBeforeParent = generateBlockWithTimestampBeforeParent(lastGenerator, blocks.last.timestamp)
    history.append(blockGeneratedBeforeParent).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == "Block had been generated before parent block had been generated")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockWithTheSameSlotAsParent = generateBlockForTheSameSlot(generators)
    history.append(blockWithTheSameSlotAsParent).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == "Block absolute slot number is equal or less than parent block")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }
    //
    val blockGeneratedWithSkippedEpoch = generateBlockWithSkippedEpoch(lastGenerator, blocks.last.timestamp, slotLengthInSeconds * epochSizeInSlots)
    history.append(blockGeneratedWithSkippedEpoch).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == "Whole epoch had been skipped")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }


    /////////// VRF verification /////////////////
    val fullConsensusInfo = history.getFullConsensusEpochInfoForBlock(blocks.last.id, history.blockToBlockInfo(blocks.last).get)

    val blockGeneratedWithIncorrectNonce = generateBlockWithIncorrectNonce(lastGenerator)
    history.append(blockGeneratedWithIncorrectNonce).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectNonce.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedWithIncorrectSlot = generateBlockWithIncorrectSlot(lastGenerator)
    history.append(blockGeneratedWithIncorrectSlot).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectSlot.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedWithIncorrectVrfPublicKey = generateBlockWithIncorrectVrfPublicKey(lastGenerator)
    history.append(blockGeneratedWithIncorrectVrfPublicKey).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectVrfPublicKey.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedWithIncorrectVrfProof = generateBlockWithIncorrectVrfProof(lastGenerator)
    history.append(blockGeneratedWithIncorrectVrfProof).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectVrfProof.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }


    /////////// Forger box verification /////////////////
    val blockGeneratedWithIncorrectForgerBoxRewardProposition = generateBlockWithIncorrectForgerBoxRewardProposition(lastGenerator)
    history.append(blockGeneratedWithIncorrectForgerBoxRewardProposition).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forger box merkle path in block ${blockGeneratedWithIncorrectForgerBoxRewardProposition.id} is inconsistent to stakes merkle root hash"))
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedWithIncorrectForgerBoxProposition = generateBlockWithIncorrectForgerBoxProposition(lastGenerator)
    history.append(blockGeneratedWithIncorrectForgerBoxProposition).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forger box merkle path in block ${blockGeneratedWithIncorrectForgerBoxProposition.id} is inconsistent to stakes merkle root hash"))
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedWithIncorrectForgerNonce = generateBlockWithIncorrectForgerBoxNonce(lastGenerator)
    history.append(blockGeneratedWithIncorrectForgerNonce).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forger box merkle path in block ${blockGeneratedWithIncorrectForgerNonce.id} is inconsistent to stakes merkle root hash"))
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    val blockGeneratedWithIncorrectValue = generateBlockWithIncorrectForgerBoxValue(lastGenerator)
    history.append(blockGeneratedWithIncorrectValue).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forger box merkle path in block ${blockGeneratedWithIncorrectValue.id} is inconsistent to stakes merkle root hash"))
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    /////////// Stake verification /////////////////

    val blockWithNotEnoughStake = generateBlockWithNotEnoughStake(lastGenerator)
    history.append(blockWithNotEnoughStake).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == s"Stake value in forger box in block ${blockWithNotEnoughStake.id} is not enough for to be forger.")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }
  }

  def generateBlockInTheFuture(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedTimestamp = Some(Instant.now.getEpochSecond + 1000))
    generator.tryToGenerateCorrectBlock(generationRules)._2.right.get.block
  }

  def generateBlockWithTimestampBeforeParent(generator: SidechainBlocksGenerator, previousBlockTimestamp: Long): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedTimestamp = Some(previousBlockTimestamp - 1))
    generator.tryToGenerateCorrectBlock(generationRules)._2.right.get.block
  }

  def generateBlockForTheSameSlot(generators: Seq[SidechainBlocksGenerator]): SidechainBlock = {
    val generator = generators(generators.size - 2) //get prelast
    val bestBlockId = generators.last.lastBlockId
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedParentId = Some(bestBlockId))
    generator.tryToGenerateCorrectBlock(generationRules)._2.right.get.block
  }

  def generateBlockWithSkippedEpoch(generator: SidechainBlocksGenerator, previousBlockTimestamp: Long, epochLengthInSeconds: Long): SidechainBlock = {
    val generationRules =
      GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedTimestamp = Some(previousBlockTimestamp + epochLengthInSeconds * 2))
    generator.tryToGenerateCorrectBlock(generationRules)._2.right.get.block
  }

  def generateBlockWithIncorrectNonce(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules = generationRules.copy(corruption = generationRules.corruption.copy(consensusNonceShift =  BigInteger.valueOf(42)))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectSlot(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules = generationRules.copy(corruption = generationRules.corruption.copy(timestampShiftInSlots = 1, consensusSlotShift = 2))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectVrfPublicKey(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgerBoxCorruption = ForgerBoxCorruptionRules(vrfPubKeyChanged = true)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgerBoxCorruptionRules = Some(forgerBoxCorruption)))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectVrfProof(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forcedVrfProof = Some(VrfGenerator.generateProof(rnd.nextLong()))))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectForgerBoxRewardProposition(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgerBoxCorruption = ForgerBoxCorruptionRules(rewardPropositionChanged = true)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgerBoxCorruptionRules = Some(forgerBoxCorruption)))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectForgerBoxProposition(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgerBoxCorruption = ForgerBoxCorruptionRules(propositionChanged = true)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgerBoxCorruptionRules = Some(forgerBoxCorruption)))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectForgerBoxNonce(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgerBoxCorruption = ForgerBoxCorruptionRules(nonceShift = 1)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgerBoxCorruptionRules = Some(forgerBoxCorruption)))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithIncorrectForgerBoxValue(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgerBoxCorruption = ForgerBoxCorruptionRules(valueShift = 1)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgerBoxCorruptionRules = Some(forgerBoxCorruption)))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }

  def generateBlockWithNotEnoughStake(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(stakeCheckCorruption = true))
    generator.tryToGenerateCorrectBlock(corruptedRules)._2.right.get.block
  }
}

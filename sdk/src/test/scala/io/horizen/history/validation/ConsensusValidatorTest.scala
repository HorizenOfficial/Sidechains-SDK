package io.horizen.history.validation

import io.horizen.account.block.AccountBlockSerializer
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.history.AccountHistory
import io.horizen.consensus.{FullConsensusEpochInfo, HistoryConsensusChecker}
import io.horizen.fixtures.VrfGenerator
import io.horizen.fixtures.sidechainblock.generation.{ForgingStakeCorruptionRules, GenerationRules, SidechainBlocksGenerator}
import io.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.params.TestNetParams
import io.horizen.utils.BytesUtils
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.history.SidechainHistory
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.time.Instant
import java.util.Random
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class ConsensusValidatorTest extends JUnitSuite with HistoryConsensusChecker {
  val rnd = new Random(20)
  val maximumAvailableShift = 2

  ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")

  private def createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots: Int, slotLengthInSeconds: Int, totalBlocksCount: Int, blocksInHistoryCount: Int):
  (SidechainHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock]) = {
    var res: Option[(SidechainHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock])] =  None
    var iteration = 0
    while (res.isEmpty) {
      val resTry = Try(createHistoryWithBlocksNoForks(new Random(rnd.nextLong()), epochSizeInSlots, slotLengthInSeconds, totalBlocksCount, blocksInHistoryCount))
      resTry match {
        case (Success(_)) => res = resTry.toOption
        case Failure(exception) => {
          println(exception.printStackTrace())
          iteration = iteration + 1
        }
      }

      if (resTry.isFailure) println(resTry.failed.get.printStackTrace())
      res = resTry.toOption

      require(iteration < 500, "Cannot generate blocks chain for test, block generation is broken")
    }
    (res.get._1, res.get._2, res.get._3)
  }

  private def createHistoryWithBlocksNoForks(rnd: Random, epochSizeInSlots: Int, slotLengthInSeconds: Int, totalBlockCount: Int, blocksInHistoryCount: Int):
    (SidechainHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock]) = {

    val genesisTimestamp: Long = Instant.now.getEpochSecond - (slotLengthInSeconds * totalBlockCount)

    val initialParams = TestNetParams(
      consensusSlotsInEpoch = epochSizeInSlots,
      consensusSecondsInSlot = slotLengthInSeconds,
      sidechainGenesisBlockTimestamp = genesisTimestamp)

    val (params, genesisBlock, genesisGenerator, genesisForgingData, genesisEndEpochInfo) = SidechainBlocksGenerator.startSidechain(10000000000L, rnd.nextInt(), initialParams)

    var history: SidechainHistory = createHistory(params, genesisBlock, genesisEndEpochInfo)
    history.applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(genesisEndEpochInfo.stakeConsensusEpochInfo, genesisEndEpochInfo.nonceConsensusEpochInfo))
    println(s"//////////////// Genesis epoch ${genesisBlock.id} had been ended ////////////////")

    var lastGenerator: SidechainBlocksGenerator = genesisGenerator
    val generators = mutable.Buffer(genesisGenerator)
    val generatedBlocks = mutable.Buffer(genesisBlock)

    for (i <- 1 to totalBlockCount) {
      val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, lastGenerator.getNotSpentBoxes)

      val (gens, generatedBlock) = generateBlock(generationRules, lastGenerator, history)
      if (i < blocksInHistoryCount) {
        history = historyUpdateShallBeSuccessful(history, generatedBlock)
        generatedBlocks.append(generatedBlock)
        generators.appendAll(gens)
        println(s"Generate normal block ${generatedBlock.id}")
      }
      else {
        println(s"Generate extra block ${generatedBlock.id}")
      }

      lastGenerator = gens.last
    }

    (history, generators, generatedBlocks)
  }

  @Test
  def blockInFutureCheck(): Unit = {
    val epochSizeInSlots = 15
    val slotLengthInSeconds = 20
    val totalBlocks = epochSizeInSlots * 4 - 2
    val (history: SidechainHistory, generators: Seq[SidechainBlocksGenerator], _) = createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots, slotLengthInSeconds, totalBlocks, totalBlocks - maximumAvailableShift)

    val lastGenerator = generators.last

    println("Test blockInFuture")
    val blockInFuture = generateBlockInTheFuture(lastGenerator, Instant.now().getEpochSecond + slotLengthInSeconds)
    history.append(blockInFuture).failed.get match {
      case expected: SidechainBlockSlotInFutureException => assert(expected.getMessage == "Block had been generated in the future")
      case nonExpected => assert(false, s"Got incorrect exception: $nonExpected")
    }
  }

  @Test
  def nonGenesisBlockCheck(): Unit = {
    val epochSizeInSlots = 15
    val slotLengthInSeconds = 20
    val totalBlocks = epochSizeInSlots * 4
    val (history: SidechainHistory, generators: Seq[SidechainBlocksGenerator], blocks) = createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots, slotLengthInSeconds, totalBlocks, totalBlocks - maximumAvailableShift)

    val lastGenerator = generators.last

    /////////// Timestamp related checks //////////////
    println("Test blockGeneratedBeforeParent")
    val blockGeneratedBeforeParent = generateBlockWithTimestampBeforeParent(lastGenerator, blocks.last.timestamp)
    history.append(blockGeneratedBeforeParent).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == "Block had been generated before parent block had been generated")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    println("Test blockWithTheSameSlotAsParent")
    val blockWithTheSameSlotAsParent = generateBlockForTheSameSlot(generators)
    history.append(blockWithTheSameSlotAsParent).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == "Block absolute slot number is equal or less than parent block")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    println("Test blockGeneratedWithSkippedEpoch")
    val blockGeneratedWithSkippedEpoch = generateBlockWithSkippedEpoch(lastGenerator, blocks.last.timestamp, slotLengthInSeconds * epochSizeInSlots)
    history.append(blockGeneratedWithSkippedEpoch).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == "Whole epoch had been skipped")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    /////////// VRF verification /////////////////
    println("Test blockGeneratedWithIncorrectNonce")
    val blockGeneratedWithIncorrectNonce = generateBlockWithIncorrectNonce(lastGenerator)
    history.append(blockGeneratedWithIncorrectNonce).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectNonce.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    println("Test blockGeneratedWithIncorrectSlot")
    val blockGeneratedWithIncorrectSlot = generateBlockWithIncorrectSlot(lastGenerator)
    history.append(blockGeneratedWithIncorrectSlot).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectSlot.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    println("Test blockGeneratedWithIncorrectVrfPublicKey")
    val blockGeneratedWithIncorrectVrfPublicKey = generateBlockWithIncorrectVrfPublicKey(lastGenerator)
    history.append(blockGeneratedWithIncorrectVrfPublicKey).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectVrfPublicKey.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    println("Test blockGeneratedWithIncorrectVrfProof")
    val blockGeneratedWithIncorrectVrfProof = generateBlockWithIncorrectVrfProof(lastGenerator)
    history.append(blockGeneratedWithIncorrectVrfProof).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectVrfProof.id} had been failed")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    /////////// Forging stake verification /////////////////
    println("Test generateBlockWithIncorrectForgingStakeBlockSignProposition")
    val blockGeneratedWithIncorrectForgingStakeBlockSignProposition = generateBlockWithIncorrectForgingStakeBlockSignProposition(lastGenerator)
    history.append(blockGeneratedWithIncorrectForgingStakeBlockSignProposition).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forging stake merkle path in block ${blockGeneratedWithIncorrectForgingStakeBlockSignProposition.id} is inconsistent to stakes merkle root hash"))
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    println("Test blockGeneratedWithIncorrectStakeAmount")
    val blockGeneratedWithIncorrectStakeAmount = generateBlockWithIncorrectForgingStakeAmount(lastGenerator)
    history.append(blockGeneratedWithIncorrectStakeAmount).failed.get match {
      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forging stake merkle path in block ${blockGeneratedWithIncorrectStakeAmount.id} is inconsistent to stakes merkle root hash"))
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }

    /////////// Stake verification /////////////////
    println("Test blockWithNotEnoughStake")
    val blockWithNotEnoughStake = generateBlockWithNotEnoughStake(lastGenerator)
    history.append(blockWithNotEnoughStake).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == s"Stake value in forger box in block ${blockWithNotEnoughStake.id} is not enough for to be forger.")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }
  }

//  @Test
//  def anotherFeeBlockCheck(): Unit = {
//    val epochSizeInSlots = 15
//    val slotLengthInSeconds = 20
//    val totalBlocks = epochSizeInSlots * 4
//    val (history: AccountHistory, generators: Seq[SidechainBlocksGenerator], blocks) = createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots, slotLengthInSeconds, totalBlocks, totalBlocks - maximumAvailableShift)
//
//    val lastGenerator = generators.last
//
//    val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
//    val blockBytes = BytesUtils.fromHexString("02e1eceb9f9e4d390ee34063b573b90123f0b36d7ff1b3120f6d5b2fb10f289627f6dddccc0c6e3bda4dfddf67e293362514c36142f70862dab22cd3609face526aec9b1c809dbfb30791dbc1b1d0140fea9c49cd2ca0d6aade8139ee919cc4795e11ae9c10400808cb0e1490201104469c8cd0addeff670801fa8dd9bc69536df036d50ed772bb2cae4e7b37b07432f5977e5e6cb239fb20084b1bd614b90e0adcc55ede058d20986e66de8e03800cfc4787f5f0ac8558d44311ea846412ce44c1c8dd42b135bad31e016b4f41a3be703817d8afc936da39b56be29d31dd37c9e509c45a710401d15de373503b51471882991cb1728b4668aeb2ed7170857cf72474413ed5be9bdb81958869c331556e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b42100000000000000000000000000000000000000000000000000000000000000002e22ffcfdaa460d18b598bb7cf5b3fc31052d0ab746a2857dc93e91f4cdca2a156e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b42100c8f107a09cd4f463afc2f1e6e5bf6022ad46000a04a817c80002000801c9c38000000000000000000000000000000000000000000000000000000000000000000056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b4210000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000027cc0eba54469b2fe62a2724dc4b961a0253c5aa001f5b070a345a29067f90bb4400848e4ecc804f64be56a52a2c94098c4aa9ef840d700083535510cb0f950700000000")
//    val serializer = new AccountBlockSerializer(sidechainTransactionsCompanion)
//    val block = serializer.parseBytes(blockBytes)
//
//    history.append(block).failed.get match {
//      case expected: IllegalArgumentException => assert(expected.getMessage == "Block had been generated before parent block had been generated")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }

//    /////////// Timestamp related checks //////////////
//    println("Test blockGeneratedBeforeParent")
//    val blockGeneratedBeforeParent = generateBlockWithTimestampBeforeParent(lastGenerator, blocks.last.timestamp)
//    history.append(blockGeneratedBeforeParent).failed.get match {
//      case expected: IllegalArgumentException => assert(expected.getMessage == "Block had been generated before parent block had been generated")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    println("Test blockWithTheSameSlotAsParent")
//    val blockWithTheSameSlotAsParent = generateBlockForTheSameSlot(generators)
//    history.append(blockWithTheSameSlotAsParent).failed.get match {
//      case expected: IllegalArgumentException => assert(expected.getMessage == "Block absolute slot number is equal or less than parent block")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    println("Test blockGeneratedWithSkippedEpoch")
//    val blockGeneratedWithSkippedEpoch = generateBlockWithSkippedEpoch(lastGenerator, blocks.last.timestamp, slotLengthInSeconds * epochSizeInSlots)
//    history.append(blockGeneratedWithSkippedEpoch).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage == "Whole epoch had been skipped")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    /////////// VRF verification /////////////////
//    println("Test blockGeneratedWithIncorrectNonce")
//    val blockGeneratedWithIncorrectNonce = generateBlockWithIncorrectNonce(lastGenerator)
//    history.append(blockGeneratedWithIncorrectNonce).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectNonce.id} had been failed")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    println("Test blockGeneratedWithIncorrectSlot")
//    val blockGeneratedWithIncorrectSlot = generateBlockWithIncorrectSlot(lastGenerator)
//    history.append(blockGeneratedWithIncorrectSlot).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectSlot.id} had been failed")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    println("Test blockGeneratedWithIncorrectVrfPublicKey")
//    val blockGeneratedWithIncorrectVrfPublicKey = generateBlockWithIncorrectVrfPublicKey(lastGenerator)
//    history.append(blockGeneratedWithIncorrectVrfPublicKey).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectVrfPublicKey.id} had been failed")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    println("Test blockGeneratedWithIncorrectVrfProof")
//    val blockGeneratedWithIncorrectVrfProof = generateBlockWithIncorrectVrfProof(lastGenerator)
//    history.append(blockGeneratedWithIncorrectVrfProof).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage == s"VRF check for block ${blockGeneratedWithIncorrectVrfProof.id} had been failed")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    /////////// Forging stake verification /////////////////
//    println("Test generateBlockWithIncorrectForgingStakeBlockSignProposition")
//    val blockGeneratedWithIncorrectForgingStakeBlockSignProposition = generateBlockWithIncorrectForgingStakeBlockSignProposition(lastGenerator)
//    history.append(blockGeneratedWithIncorrectForgingStakeBlockSignProposition).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forging stake merkle path in block ${blockGeneratedWithIncorrectForgingStakeBlockSignProposition.id} is inconsistent to stakes merkle root hash"))
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    println("Test blockGeneratedWithIncorrectStakeAmount")
//    val blockGeneratedWithIncorrectStakeAmount = generateBlockWithIncorrectForgingStakeAmount(lastGenerator)
//    history.append(blockGeneratedWithIncorrectStakeAmount).failed.get match {
//      case expected: IllegalStateException => assert(expected.getMessage.contains(s"Forging stake merkle path in block ${blockGeneratedWithIncorrectStakeAmount.id} is inconsistent to stakes merkle root hash"))
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//
//    /////////// Stake verification /////////////////
//    println("Test blockWithNotEnoughStake")
//    val blockWithNotEnoughStake = generateBlockWithNotEnoughStake(lastGenerator)
//    history.append(blockWithNotEnoughStake).failed.get match {
//      case expected: IllegalArgumentException => assert(expected.getMessage == s"Stake value in forger box in block ${blockWithNotEnoughStake.id} is not enough for to be forger.")
//      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
//    }
//  }

  // TODO: this corruption doesn't work anymore, because vrf is verified before timestamp now.
  def generateBlockInTheFuture(generator: SidechainBlocksGenerator, forcedTimestamp: Long): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedTimestamp = Some(forcedTimestamp))
    generateBlock(generationRules, generator)._2
  }

  def generateBlockWithTimestampBeforeParent(generator: SidechainBlocksGenerator, previousBlockTimestamp: Long): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedTimestamp = Some(previousBlockTimestamp - 1))
    generateBlock(generationRules, generator)._2
  }

  def generateBlockForTheSameSlot(generators: Seq[SidechainBlocksGenerator]): SidechainBlock = {
    val preLastGenerator = generators(generators.size - 2) //get prelast
    val bestBlockId = generators.last.lastBlockId
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, preLastGenerator.getNotSpentBoxes).copy(forcedParentId = Some(bestBlockId))
    generateBlock(generationRules, preLastGenerator)._2
  }

  def generateBlockWithSkippedEpoch(generator: SidechainBlocksGenerator, previousBlockTimestamp: Long, epochLengthInSeconds: Long): SidechainBlock = {
    val generationRules =
      GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes).copy(forcedTimestamp = Some(previousBlockTimestamp + epochLengthInSeconds * 2))
    generateBlock(generationRules, generator)._2
  }

  def generateBlockWithIncorrectNonce(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules = generationRules.copy(corruption = generationRules.corruption.copy(consensusNonceShift =  42))
    generateBlock(corruptedRules, generator)._2

  }

  def generateBlockWithIncorrectSlot(generator: SidechainBlocksGenerator): SidechainBlock = {
    val consensusSlotShift = 2
    require(consensusSlotShift <= maximumAvailableShift)

    val timestampSlotShift = 1
    require(timestampSlotShift <= maximumAvailableShift)

    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules = generationRules.copy(corruption = generationRules.corruption.copy(timestampShiftInSlots = timestampSlotShift, consensusSlotShift = consensusSlotShift))
    generateBlock(corruptedRules, generator)._2

  }

  def generateBlockWithIncorrectVrfPublicKey(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgingStakeCorruption = ForgingStakeCorruptionRules(vrfPubKeyChanged = true)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgingStakeCorruptionRules = Some(forgingStakeCorruption)))
    generateBlock(corruptedRules, generator)._2
  }

  def generateBlockWithIncorrectVrfProof(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forcedVrfProof = Some(VrfGenerator.generateProof(rnd.nextLong()))))
    generateBlock(corruptedRules, generator)._2
  }

  def generateBlockWithIncorrectForgingStakeBlockSignProposition(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgingStakeCorruption = ForgingStakeCorruptionRules(blockSignPropositionChanged = true)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgingStakeCorruptionRules = Some(forgingStakeCorruption)))
    generateBlock(corruptedRules, generator)._2
  }

  def generateBlockWithIncorrectForgingStakeAmount(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val forgingStakeCorruption = ForgingStakeCorruptionRules(stakeAmountShift = 1)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(forgingStakeCorruptionRules = Some(forgingStakeCorruption)))
    generateBlock(corruptedRules, generator)._2
  }

  def generateBlockWithNotEnoughStake(generator: SidechainBlocksGenerator): SidechainBlock = {
    val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, generator.getNotSpentBoxes)
    val corruptedRules =
      generationRules.copy(corruption = generationRules.corruption.copy(stakeCheckCorruption = true))
    generateBlock(corruptedRules, generator)._2
  }
}

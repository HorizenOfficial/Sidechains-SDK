package io.horizen.utils

import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion1}
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.consensus.{ConsensusParamsUtil, intToConsensusEpochNumber, intToConsensusSlotNumber}
import io.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import io.horizen.account.fork.ConsensusParamsFork
import io.horizen.fork.{ForkConfigurator, ForkManager, ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch, SimpleForkConfigurator}
import io.horizen.params.NetworkParams
import io.horizen.proposition.SchnorrProposition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.util.{ModifierId, bytesToId}
import sparkz.core.block.Block

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import java.math.BigInteger
import java.util

class TimeToEpochUtilsTest extends JUnitSuite {

  case class StubbedNetParams(override val sidechainGenesisBlockTimestamp: Block.Timestamp,
                              override val consensusSecondsInSlot: Int,
                              override val consensusSlotsInEpoch: Int) extends NetworkParams {
    override val sidechainId: Array[Byte] = new Array[Byte](32)
    override val sidechainGenesisBlockId: ModifierId = bytesToId(new Array[Byte](32))
    override val genesisMainchainBlockHash: Array[Byte] = new Array[Byte](32)
    override val parentHashOfGenesisMainchainBlock: Array[Byte] = new Array[Byte](32)
    override val genesisPoWData: Seq[(Int, Int)] = Seq()
    override val mainchainCreationBlockHeight: Int = 1
    override val EquihashN: Int = 200
    override val EquihashK: Int = 9
    override val EquihashCompactSizeLength: Int = 3
    override val EquihashSolutionLength: Int = 1344
    override val withdrawalEpochLength: Int = 100
    override val powLimit: BigInteger = new BigInteger("07ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
    override val nPowAveragingWindow: Int = 17
    override val nPowMaxAdjustDown: Int = 32 // 32% adjustment down
    override val nPowMaxAdjustUp: Int = 16 // 16% adjustment up
    override val nPowTargetSpacing: Int = 150 // 2.5 * 60
    override val signersPublicKeys: Seq[SchnorrProposition] = Seq()
    override val mastersPublicKeys: Seq[SchnorrProposition] = Seq()
    override val circuitType: CircuitTypes = CircuitTypes.NaiveThresholdSignatureCircuit
    override val signersThreshold: Int = 0
    override val certProvingKeyFilePath: String = ""
    override val certVerificationKeyFilePath: String = ""
    override val calculatedSysDataConstant: Array[Byte] = new Array[Byte](32)
    override val initialCumulativeCommTreeHash: Array[Byte] = Array()
    override val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] = Seq()
    override val cswProvingKeyFilePath: String = ""
    override val cswVerificationKeyFilePath: String = ""
    override val sidechainCreationVersion: SidechainCreationVersion = SidechainCreationVersion1
    override val chainId: Long = 11111111
    override val isCSWEnabled: Boolean = true
    override val isNonCeasing: Boolean = false
    override val minVirtualWithdrawalEpochLength: Int = 10
  }

  val consensusForkActivationRegtest1 = 20
  val consensusForkActivationRegtest2 = 30
  val consensusForkActivationRegtest3 = 50

  val consensusForkSlotsPerEpochRegtest1: Int = 1000
  val consensusForkSlotsPerEpochRegtest2: Int = 300
  val consensusForkSlotsPerEpochRegtest3: Int = 750

  val defaultConsensusFork = ConsensusParamsFork.DefaultConsensusParamsFork
  // 1000 * (30-20) = 10000 slots in fork 1
  val consensusFork1 = new ConsensusParamsFork(consensusForkSlotsPerEpochRegtest1)
  // 300 * (50-30) = 6000 slots in fork 2
  val consensusFork2 = new ConsensusParamsFork(consensusForkSlotsPerEpochRegtest2)
  val consensusFork3 = new ConsensusParamsFork(consensusForkSlotsPerEpochRegtest3)

  private def checkSlotAndEpoch(timeStamp: Block.Timestamp,
                                expectedSlot: Int,
                                expectedEpoch: Int)(implicit params: StubbedNetParams): Unit = {
    assertEquals("Epoch shall be as expected", expectedEpoch, TimeToEpochUtils.timeStampToEpochNumber(params, timeStamp))
    assertEquals("Slot shall be as expected", expectedSlot, TimeToEpochUtils.timeStampToSlotNumber(params, timeStamp))
    val expectedAbsoluteSlot = expectedEpoch * params.consensusSlotsInEpoch + expectedSlot
    assertEquals("Absolute slot shall be as expected", expectedAbsoluteSlot, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, timeStamp))

    val slotAndEpoch = TimeToEpochUtils.timestampToEpochAndSlot(params, timeStamp)
    assertEquals("Epoch shall be as expected", expectedEpoch, slotAndEpoch.epochNumber)
    assertEquals("Slot shall be as expected", expectedSlot, slotAndEpoch.slotNumber)

  }

  @Test
  def init(): Unit = {
    val forkConfigurator = new SimpleForkConfigurator
    ForkManagerUtil.initializeForkManager(forkConfigurator, "regtest")
  }


  @Test
  def getEpochIndexWithSameSlotsTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000

    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)

    //Initially we have 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The first fork has 720 slots per epoch of 1s and it contains 10 epochs = 720 * 10 * 1 = 7200s
    //The second fork has 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The third fork has 720 slots per epoch of 1s

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, defaultConsensusFork),
      (consensusForkActivationRegtest2, defaultConsensusFork),
      (consensusForkActivationRegtest3, defaultConsensusFork)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Genesis block timestamp must be inside the epoch 1", 1, TimeToEpochUtils.timeStampToEpochNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with a block in the initial fork /////////////////
    val initialForkBlockTimestmap = 9000
    assertEquals("Expected epoch should be 13 => (9000 - virtualGenesisBlockTimestamp (1000 - 720 + 1)) / 720 = 12.1 => abs(12.1) + 1 = 13", 13, TimeToEpochUtils.timeStampToEpochNumber(params, initialForkBlockTimestmap))

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    val firstForkBlockTimestmap = 14400
    assertEquals("Expected epoch should be 20 => (14400 - virtualGenesisBlockTimestamp (281)) / 720 = 19.6 => abs(19.6) + 1 = 20", 20, TimeToEpochUtils.timeStampToEpochNumber(params, firstForkBlockTimestmap))

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    val firstForkBlockTimestmap2 = 22000
    assertEquals("Expected epoch should be 31 => 20 + ((22000 - 14400 - virtualGenesisBlockTimestamp (281)) / 720) = 30.1 => abs(30.1) + 1 = 31 ", 31, TimeToEpochUtils.timeStampToEpochNumber(params, firstForkBlockTimestmap2))

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    val secondForkBlockTimestmap = 28000
    assertEquals("Expected epoch should be 39 => 30 + (28000 - 14400 - 7200 - virtualGenesisBlockTimestamp (281)) / 720 = 38.4 => abs(38.4) + 1 = 39", 39, TimeToEpochUtils.timeStampToEpochNumber(params, secondForkBlockTimestmap))

    ///////////////////////  Test with a block at the end of the third fork /////////////////
    val thirdForkBlockTimestmap = 35000
    assertEquals("Expected epoch should be 49 => 30 + (35000 - 14400 - 7200 - virtualGenesisBlockTimestamp (281)) / 720 = 48.2 => abs(48.2) + 1 = 49", 49, TimeToEpochUtils.timeStampToEpochNumber(params, thirdForkBlockTimestmap))
  }

  @Test
  def getEpochIndexTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000

    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)

    //Initially we have 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The first fork has 1000 slots per epoch of 1s and it contains 10 epochs = 1000 * 10 * 1 = 10000s
    //The second fork has 300 slots per epoch of 1s and it contains 20 epochs = 300 * 20 * 1 = 6000s
    //The third fork has 750 slots per epoch of 1s

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, consensusFork1),
      (consensusForkActivationRegtest2, consensusFork2),
      (consensusForkActivationRegtest3, consensusFork3)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Genesis block timestamp must be inside the epoch 1", 1, TimeToEpochUtils.timeStampToEpochNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with a block in the initial fork /////////////////
    val initialForkBlockTimestmap = 9000
    assertEquals("Expected epoch should be 13 => (9000 - virtualGenesisBlockTimestamp (1000 - 720 + 1)) / 720 = 12.1 => abs(12.1) + 1 = 13", 13, TimeToEpochUtils.timeStampToEpochNumber(params, initialForkBlockTimestmap))

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    val firstForkBlockTimestmap = 14400
    assertEquals("Expected epoch should be 20 => (14400 - virtualGenesisBlockTimestamp (281)) / 720 = 19.6 => abs(19.6) + 1 = 20", 20, TimeToEpochUtils.timeStampToEpochNumber(params, firstForkBlockTimestmap))

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    val firstForkBlockTimestmap2 = 22000
    assertEquals("Expected epoch should be 28 => 20 + ((22000 - 14400 - virtualGenesisBlockTimestamp (281)) / 1000) = 27.3 => abs(27.3) + 1 = 28 ", 28, TimeToEpochUtils.timeStampToEpochNumber(params, firstForkBlockTimestmap2))

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    val secondForkBlockTimestmap = 28000
    assertEquals("Expected epoch should be 42 => 30 + (28000 - 14400 - 10000 - virtualGenesisBlockTimestamp (281)) / 300 = 41 => abs(41) + 1 = 42", 42, TimeToEpochUtils.timeStampToEpochNumber(params, secondForkBlockTimestmap))

    ///////////////////////  Test with a block in the third fork /////////////////
    val thirdForkBlockTimestmap = 35000
    assertEquals("Expected epoch should be 56 => 50 + (35000 - 14400 - 10000 - 6000 - virtualGenesisBlockTimestamp (281)) / 300 = 55,7 => abs(55,7) + 1 = 56", 56, TimeToEpochUtils.timeStampToEpochNumber(params, thirdForkBlockTimestmap))
  }

  @Test
  def timeStampToSlotNumberWithSameSlotsTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000

    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)

    //Initially we have 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The first fork has 720 slots per epoch of 1s and it contains 10 epochs = 720 * 10 * 1 = 7200s
    //The second fork has 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The third fork has 720 slots per epoch of 1s

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, defaultConsensusFork),
      (consensusForkActivationRegtest2, defaultConsensusFork),
      (consensusForkActivationRegtest3, defaultConsensusFork)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Slot number should be 720 => epoch=1-1 fork=0 => 1000 - 0*720 - 281 + 1", 720, TimeToEpochUtils.timeStampToSlotNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with a block in the initial fork /////////////////
    val initialForkBlockTimestmap = 9000
    assertEquals("Slot number should be 80 => epoch=13-1 fork=0 => 9000 - 12*720 - 281 + 1 = 80 ", 80, TimeToEpochUtils.timeStampToSlotNumber(params, initialForkBlockTimestmap))

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    val firstForkBlockTimestmap = 14400
    assertEquals("Slot number should be 440 => epoch=20-1 fork=1 => (14400 - 19*720 - 281) % 720 + 1= 440", 440, TimeToEpochUtils.timeStampToSlotNumber(params, firstForkBlockTimestmap))

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    val firstForkBlockTimestmap2 = 22000
    assertEquals("Slot number should be 120 => epoch=31-1 fork=1 => (22000 - 20*720 - 9*720 - 281) % 720 + 1= 120", 120, TimeToEpochUtils.timeStampToSlotNumber(params, firstForkBlockTimestmap2))

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    val secondForkBlockTimestmap = 28000
    assertEquals("Slot number should be 360 => epoch=39-1 fork=1 => (28000 - 20*720 - 10*720 - 8*720 - 281) % 720 + 1= 360", 360, TimeToEpochUtils.timeStampToSlotNumber(params, secondForkBlockTimestmap))

    ///////////////////////  Test with a block at the end of the third fork /////////////////
    val thirdForkBlockTimestmap = 35000
    assertEquals("Slot number should be 160 => epoch=49-1 fork=1 => (35000 - 20*720 - 10*720 - 18*720 - 281) % 750 + 1= 160", 160, TimeToEpochUtils.timeStampToSlotNumber(params, thirdForkBlockTimestmap))

  }

  @Test
  def timeStampToSlotNumberTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000

    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)

    //Initially we have 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The first fork has 1000 slots per epoch of 1s and it contains 10 epochs = 1000 * 10 * 1 = 10000s
    //The second fork has 300 slots per epoch of 1s and it contains 20 epochs = 300 * 20 * 1 = 6000s
    //The third fork has 750 slots per epoch of 1s

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, consensusFork1),
      (consensusForkActivationRegtest2, consensusFork2),
      (consensusForkActivationRegtest3, consensusFork3)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Slot number should be 720 => epoch=1-1 fork=0 => 1000 - 0*720 - 281 + 1", 720, TimeToEpochUtils.timeStampToSlotNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with a block in the initial fork /////////////////
    val initialForkBlockTimestmap = 9000
    assertEquals("Slot number should be 80 => epoch=13-1 fork=0 => 9000 - 12*720 - 281 + 1 = 80 ", 80, TimeToEpochUtils.timeStampToSlotNumber(params, initialForkBlockTimestmap))

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    val firstForkBlockTimestmap = 14400
    assertEquals("Slot number should be 440 => epoch=20-1 fork=1 => (14400 - 19*720 - 281) % 720 + 1= 440", 440, TimeToEpochUtils.timeStampToSlotNumber(params, firstForkBlockTimestmap))

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    val firstForkBlockTimestmap2 = 22000
    assertEquals("Slot number should be 320 => epoch=28-1 fork=1 => (22000 - 20*720 - 281) % 1000 + 1= 320", 320, TimeToEpochUtils.timeStampToSlotNumber(params, firstForkBlockTimestmap2))

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    val secondForkBlockTimestmap = 28000
    assertEquals("Slot number should be 20 => epoch=42-1 fork=1 => (28000 - 20*720 - 10*1000 - 281) % 300 + 1= 20", 20, TimeToEpochUtils.timeStampToSlotNumber(params, secondForkBlockTimestmap))

    ///////////////////////  Test with a block in the third fork /////////////////
    val thirdForkBlockTimestmap = 35000
    assertEquals("Slot number should be 570 => epoch=56-1 fork=1 => (35000 - 20*720 - 10*1000 - 20*300 - 281) % 750 + 1= 570", 570, TimeToEpochUtils.timeStampToSlotNumber(params, thirdForkBlockTimestmap))

  }

  @Test
  def timestampToEpochAndSlotTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000

    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)

    //Initially we have 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The first fork has 1000 slots per epoch of 1s and it contains 10 epochs = 1000 * 10 * 1 = 10000s
    //The second fork has 300 slots per epoch of 1s and it contains 20 epochs = 300 * 20 * 1 = 6000s
    //The third fork has 750 slots per epoch of 1s

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, consensusFork1),
      (consensusForkActivationRegtest2, consensusFork2),
      (consensusForkActivationRegtest3, consensusFork3)
    ))

    ///////////////////////  Test with genesis block /////////////////
    val initialForkBlockTimestmap = 9000
    var consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, sidechainGenesisBlockTimestamp)
    assertEquals("Genesis block timestamp must be inside the epoch 1", 1, consensusEpochAndSlot.epochNumber)
    assertEquals("Slot number should be 720 => epoch=1-1 fork=0 => 1000 - 0*720 - 281 + 1", 720, consensusEpochAndSlot.slotNumber)

    ///////////////////////  Test with a block in the initial fork /////////////////
    consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, initialForkBlockTimestmap)
    assertEquals("Expected epoch should be 13 => (9000 - virtualGenesisBlockTimestamp (1000 - 720 + 1)) / 720 = 12.1 => abs(12.1) + 1 = 13", 13, consensusEpochAndSlot.epochNumber)
    assertEquals("Slot number should be 80 => epoch=13-1 fork=0 => 9000 - 12*720 - 281 + 1 = 80 ", 80, consensusEpochAndSlot.slotNumber)

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    val firstForkBlockTimestmap = 14400
    consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, firstForkBlockTimestmap)
    assertEquals("Expected epoch should be 20 => (14400 - virtualGenesisBlockTimestamp (281)) / 720 = 19.6 => abs(19.6) + 1 = 20", 20, consensusEpochAndSlot.epochNumber)
    assertEquals("Slot number should be 440 => epoch=20-1 fork=1 => (14400 - 19*720 - 281) % 720 + 1= 440", 440, consensusEpochAndSlot.slotNumber)

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    val firstForkBlockTimestmap2 = 22000
    consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, firstForkBlockTimestmap2)
    assertEquals("Expected epoch should be 28 => 20 + ((22000 - 14400 - virtualGenesisBlockTimestamp (281)) / 1000) = 27.3 => abs(27.3) + 1 = 28 ", 28, consensusEpochAndSlot.epochNumber)
    assertEquals("Slot number should be 320 => epoch=28-1 fork=1 => (22000 - 20*720 - 281) % 1000 + 1= 320", 320, consensusEpochAndSlot.slotNumber)

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    val secondForkBlockTimestmap = 28000
    consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, secondForkBlockTimestmap)
    assertEquals("Expected epoch should be 42 => 30 + (28000 - 14400 - 10000 - virtualGenesisBlockTimestamp (281)) / 300 = 41 => abs(41) + 1 = 42", 42, consensusEpochAndSlot.epochNumber)
    assertEquals("Slot number should be 20 => epoch=42-1 fork=1 => (28000 - 20*720 - 10*1000 - 281) % 300 + 1= 20", 20, consensusEpochAndSlot.slotNumber)

    ///////////////////////  Test with a block in the third fork /////////////////
    val thirdForkBlockTimestmap = 35000
    consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, thirdForkBlockTimestmap)
    assertEquals("Expected epoch should be 56 => 50 + (35000 - 14400 - 10000 - 6000 - virtualGenesisBlockTimestamp (281)) / 300 = 55,7 => abs(55,7) + 1 = 56", 56, consensusEpochAndSlot.epochNumber)
    assertEquals("Slot number should be 570 => epoch=56-1 fork=1 => (35000 - 20*720 - 10*1000 - 20*300 - 281) % 750 + 1= 570", 570, consensusEpochAndSlot.slotNumber)
  }

  @Test
  def timestampToAbsoluteSlotNumber(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000

    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)

    //Initially we have 720 slots per epoch of 1s and it contains 20 epochs = 720 * 20 * 1 = 14400s
    //The first fork has 1000 slots per epoch of 1s and it contains 10 epochs = 1000 * 10 * 1 = 10000s
    //The second fork has 300 slots per epoch of 1s and it contains 20 epochs = 300 * 20 * 1 = 6000s
    //The third fork has 750 slots per epoch of 1s


    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, consensusFork1),
      (consensusForkActivationRegtest2, consensusFork2),
      (consensusForkActivationRegtest3, consensusFork3)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Absolute slot number should be 1440 => epoch=1 slot=720 fork=0 => 1*720+720 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with a block in the initial fork /////////////////
    val initialForkBlockTimestmap = 9000
    assertEquals("Absolute slot number should be 9440 => genesisSlot(1440) + 9000 - genesisTimestamp(1000) = 9440", 9440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, initialForkBlockTimestmap))

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    val firstForkBlockTimestmap = 14400
    assertEquals("Absolute slot number should be 14840 => genesisSlot(1440) + 14400 - genesisTimestamp(1000) = 14840", 14840, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, firstForkBlockTimestmap))

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    val firstForkBlockTimestmap2 = 22000
    assertEquals("Absolute slot number should be 22440 => genesisSlot(1440) + 22000 - genesisTimestamp(1000) ", 22440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, firstForkBlockTimestmap2))

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    val secondForkBlockTimestmap = 28000
    assertEquals("Absolute slot number should be 28440 => genesisSlot(1440) + 28000 - genesisTimestamp(1000) ", 28440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, secondForkBlockTimestmap))

    ///////////////////////  Test with a block in the third fork /////////////////
    val thirdForkBlockTimestmap = 35000
    assertEquals("Absolute slot number should be 35440 => genesisSlot(1440) + 35000 - genesisTimestamp(1000) ", 35440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, thirdForkBlockTimestmap))
  }


  @Test
  def checkSlotAndEpoch(): Unit = {
    val consensusSlotsInEpoch = 100
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = 1990, consensusSecondsInSlot = 10, consensusSlotsInEpoch = consensusSlotsInEpoch)

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(consensusSlotsInEpoch)),
    ))

    assertEquals(" Seconds in epoch shall be as expected", 1000, TimeToEpochUtils.epochInSeconds(params, consensusSlotsInEpoch))
    checkSlotAndEpoch(1990, 100, 1)


    checkSlotAndEpoch(2000, 1, 2)
    checkSlotAndEpoch(2009, 1, 2)
    checkSlotAndEpoch(2010, 2, 2)
    checkSlotAndEpoch(2999, 100, 2)

    checkSlotAndEpoch(3000, 1, 3)
    checkSlotAndEpoch(3009, 1, 3)
    checkSlotAndEpoch(3010, 2, 3)
    checkSlotAndEpoch(3999, 100, 3)

    checkSlotAndEpoch(1234568890, 90, 1234568)
  }

  @Test
  def checkSlotAndEpoch2(): Unit = {
    val consensusSlotsInEpoch = 8
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = 61, consensusSecondsInSlot = 3, consensusSlotsInEpoch = consensusSlotsInEpoch)

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(consensusSlotsInEpoch)),
    ))

    assertEquals(" Seconds in epoch shall be as expected", 24, TimeToEpochUtils.epochInSeconds(params, params.consensusSlotsInEpoch))
    checkSlotAndEpoch(90, 1, 3)
    assertEquals(1, TimeToEpochUtils.secondsRemainingInSlot(params,90))
    checkSlotAndEpoch(91, 2, 3)
    assertEquals(3, TimeToEpochUtils.secondsRemainingInSlot(params,91))
    checkSlotAndEpoch(92, 2, 3)
    assertEquals(2, TimeToEpochUtils.secondsRemainingInSlot(params,92))
    checkSlotAndEpoch(93, 2, 3)
    assertEquals(1, TimeToEpochUtils.secondsRemainingInSlot(params,93))
    checkSlotAndEpoch(94, 3, 3)
    assertEquals(3, TimeToEpochUtils.secondsRemainingInSlot(params,94))
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectEpoch(): Unit = {
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp = 2000, consensusSecondsInSlot = 10, consensusSlotsInEpoch = 100)


    TimeToEpochUtils.timeStampToEpochNumber(params, 1999)
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectSlot(): Unit = {
    val params = StubbedNetParams(6000, 10, 100)
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(100)),
    ))

    TimeToEpochUtils.timeStampToSlotNumber(params, -5)
  }

  @Test
  def testTimestampToSlotEpochNumber(): Unit = {
    val sidechainGenesisBlockTimestamp = 1000
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = 1, consensusSlotsInEpoch = defaultConsensusFork.consensusSlotsInEpoch)


    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (consensusForkActivationRegtest1, consensusFork1),
      (consensusForkActivationRegtest2, consensusFork2),
      (consensusForkActivationRegtest3, consensusFork3)
    ))

    ///////////////////////  Test with genesis block /////////////////
    var epochNumber = 1
    var slotNumber = 720
    var timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))

    ///////////////////////  Test with a block in the initial fork /////////////////
    epochNumber = 13
    slotNumber = 80
    timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))

    ///////////////////////  Test with a block at the beginning of the first fork /////////////////
    epochNumber = 20
    slotNumber = 440
    timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))

    ///////////////////////  Test with a block in the middle of the first fork /////////////////
    epochNumber = 28
    slotNumber = 320
    timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))

    ///////////////////////  Test with a block in the middle of the second fork /////////////////
    epochNumber = 42
    slotNumber = 20
    timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))

    ///////////////////////  Test with a block in the third fork /////////////////
    epochNumber = 56
    slotNumber = 570
    timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))
  }

  class CustomForkConfigurator extends ForkConfigurator {
    /**
     * Mandatory for every sidechain to provide an epoch number here.
     */
    override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(10, 20, 0)

    override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {
      Seq(new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](SidechainForkConsensusEpoch(consensusForkSlotsPerEpochRegtest1, 20, 20), consensusFork1),
        new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](SidechainForkConsensusEpoch(consensusForkSlotsPerEpochRegtest2, 20, 20), consensusFork2),
        new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](SidechainForkConsensusEpoch(consensusForkSlotsPerEpochRegtest3, 20, 20), consensusFork3)).asJava
    }
  }
}

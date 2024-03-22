package io.horizen.utils

import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion1}
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.consensus.{ConsensusEpochAndSlot, ConsensusParamsUtil, intToConsensusEpochNumber, intToConsensusSlotNumber}
import io.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo, CustomForkConfiguratorWithConsensusParamsFork, ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.params.NetworkParams
import io.horizen.proposition.SchnorrProposition
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.util.{ModifierId, bytesToId}
import sparkz.core.block.Block

import java.math.BigInteger

class TimeToEpochUtilsTest extends JUnitSuite {

  case class StubbedNetParams(override val sidechainGenesisBlockTimestamp: Block.Timestamp) extends NetworkParams {
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
    override val mcBlockRefDelay : Int = 0
    override val mcHalvingInterval: Int = 840000
  }

  val defaultConsensusFork = ConsensusParamsFork.DefaultConsensusParamsFork

  private def checkSlotAndEpoch(timeStamp: Block.Timestamp,
                                expectedSlot: Int,
                                expectedEpoch: Int)(implicit params: StubbedNetParams): Unit = {
    assertEquals("Epoch shall be as expected", expectedEpoch, TimeToEpochUtils.timeStampToEpochNumber(params.sidechainGenesisBlockTimestamp, timeStamp))
    assertEquals("Slot shall be as expected", expectedSlot, TimeToEpochUtils.timeStampToSlotNumber(params.sidechainGenesisBlockTimestamp, timeStamp))
    val expectedAbsoluteSlot = expectedEpoch * defaultConsensusFork.consensusSlotsInEpoch + expectedSlot
    assertEquals("Absolute slot shall be as expected", expectedAbsoluteSlot, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params.sidechainGenesisBlockTimestamp, timeStamp))

    val slotAndEpoch = TimeToEpochUtils.timestampToEpochAndSlot(params.sidechainGenesisBlockTimestamp, timeStamp)
    assertEquals("Epoch shall be as expected", expectedEpoch, slotAndEpoch.epochNumber)
    assertEquals("Slot shall be as expected", expectedSlot, slotAndEpoch.slotNumber)

  }

  @Test
  def init(): Unit = {
    val forkConfigurator = new SimpleForkConfigurator
    ForkManagerUtil.initializeForkManager(forkConfigurator, "regtest")
  }


  @Test
  def generateAndValidateTimestampForDifferentEpochWithForks(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSecondsInSlot = 12
    val consensusSecondsInSlot2 = 5
    val consensusSecondsInSlot3 = 1

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(20, 23, 25, 28), Seq(1000,1200, 1200, 500), Seq(12, 12, 5, 1)), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, defaultConsensusFork),
      ConsensusParamsForkInfo(20, new ConsensusParamsFork(1000, consensusSecondsInSlot)),
      ConsensusParamsForkInfo(23, new ConsensusParamsFork(1200, consensusSecondsInSlot)),
      ConsensusParamsForkInfo(25, new ConsensusParamsFork(1200, consensusSecondsInSlot2)),
      ConsensusParamsForkInfo(28, new ConsensusParamsFork(500, consensusSecondsInSlot3)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(23), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(25), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(28), intToConsensusSlotNumber(1)),
    ))


    var old_ts: Long = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    var ts: Long = old_ts
    var ts_diff: Long = 0
    var consensusEpochAndSlot = ConsensusEpochAndSlot(intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    for (i <- 0 to 19) {
      for (y <- 1 to 720) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        if (i > 1) {
          assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
          assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, ts), y)
          consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(sidechainGenesisBlockTimestamp, ts)
          assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        }
        old_ts = ts
      }
    }
    for (i <- 20 to 22) {
      for (y <- 1 to 1000) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
        assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, ts), y)
        consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(sidechainGenesisBlockTimestamp, ts)
        assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        old_ts = ts
      }
    }
    for (i <- 23 to 24) {
      for (y <- 1 to 1200) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
        assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, ts), y)
        consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(sidechainGenesisBlockTimestamp, ts)
        assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        old_ts = ts
      }
    }
    for (i <- 25 to 27) {
      for (y <- 1 to 1200) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts

        //The first block of the new epoch should still be 12s later than the last block of the previous epoch
        if (i==25 && y==1)
          assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        else
          assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot2}", ts_diff, consensusSecondsInSlot2)

        assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
        assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, ts), y)
        consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(sidechainGenesisBlockTimestamp, ts)
        assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        old_ts = ts
      }
    }
    for (i <- 28 to 30) {
      for (y <- 1 to 500) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts

        //The first block of the new epoch should still be 12s later than the last block of the previous epoch
        if (i==28 && y==1)
          assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot2}", ts_diff, consensusSecondsInSlot2)
        else
          assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot3}", ts_diff, consensusSecondsInSlot3)

        assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
        assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, ts), y)
        consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(sidechainGenesisBlockTimestamp, ts)
        assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        old_ts = ts
      }
    }
  }

  @Test
  def generateAndValidateTimestampForDifferentEpochNoForks(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSlotsInEpoch = 720
    val consensusSecondsInSlot = 12

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(consensusSlotsInEpoch, consensusSecondsInSlot)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp)
    ))

    var old_ts: Long = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    var ts: Long = old_ts
    var ts_diff: Long = 0
    var consensusEpochAndSlot = ConsensusEpochAndSlot(intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    for (i <- 0 to 25) {
      for (y <- 1 to 720) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp,  intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        if (i > 1) {
          assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
          assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(sidechainGenesisBlockTimestamp, ts), i)
          assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, ts), y)
          consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(sidechainGenesisBlockTimestamp, ts)
          assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        }
        old_ts = ts
      }
    }
  }

  @Test
  def timestampToAbsoluteNumberNoForksTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSlotsInEpoch = 720
    val consensusSecondsInSlot = 12

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(consensusSlotsInEpoch, consensusSecondsInSlot)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Absolute slot number should be 1440 => epoch=1 slot=720 fork=0 => 1*720 + 720 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with block at epoch:2 slot: 0 /////////////////
    var blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(2), intToConsensusSlotNumber(0))
    assertEquals("Absolute slot number should be 1440 => epoch=2 slot=0 fork=0 => (2*720) + 0 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(2), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 1441 => epoch=2 slot=1 fork=0 => (2*720) + 1 = 1441", 1441, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(2), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 2160 => epoch=2 slot=720 fork=0 => (2*720) + 720 = 1441", 2160, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 100 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(100))
    assertEquals("Absolute slot number should be 14500 => epoch=20 slot=100 fork=0 => (20*720) + 100 = 14500", 14500, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 15120 => epoch=20 slot=720 fork=0 => (20*720) + 720 = 15120", 15120, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

  }

  @Test
  def timestampToAbsoluteNumberWithForksTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSecondsInSlot = 12
    val consensusSecondsInSlot2 = 5
    val consensusSecondsInSlot3 = 1

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(20, 23, 25, 28), Seq(1000, 1200, 1200, 500), Seq(12, 12, 5, 1)), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, defaultConsensusFork),
      ConsensusParamsForkInfo(20, new ConsensusParamsFork(1000, consensusSecondsInSlot)),
      ConsensusParamsForkInfo(23, new ConsensusParamsFork(1200, consensusSecondsInSlot)),
      ConsensusParamsForkInfo(25, new ConsensusParamsFork(1200, consensusSecondsInSlot2)),
      ConsensusParamsForkInfo(28, new ConsensusParamsFork(500, consensusSecondsInSlot3)),
    ))


    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(23), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(25), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(28), intToConsensusSlotNumber(1))
    ))


    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Absolute slot number should be 1440 => epoch=1 slot=720 fork=0 => 1*720 + 720 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with block at epoch:2 slot: 0 /////////////////
    var blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp,  intToConsensusEpochNumber(2), intToConsensusSlotNumber(0))
    assertEquals("Absolute slot number should be 1440 => epoch=2 slot=0 fork=0 => (2*720) + 0 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(2), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 1441 => epoch=2 slot=1 fork=0 => (2*720) + 1 = 1441", 1441, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(2), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 2160 => epoch=2 slot=720 fork=0 => (2*720) + 720 = 1441", 2160, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:19 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(19), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 14400 => epoch=19 slot=720 fork=0 => (19*720) + 720 = 14400", 14400, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 0 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(0))
    assertEquals("Absolute slot number should be 14400 => epoch=20 slot=0 fork=0 => (20*720) + 0 = 14400", 14400, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 14400 => epoch=20 slot=1 fork=1 => (20*720) + 1 = 14401", 14401, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 721 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(20), intToConsensusSlotNumber(721))
    assertEquals("Absolute slot number should be 15121 => epoch=20 slot=721 fork=1 => (20*720) + 721 = 15121", 15121, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:22 slot: 500 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(22), intToConsensusSlotNumber(500))
    assertEquals("Absolute slot number should be 16900 => epoch=22 slot=500 fork=1 => (20*720) + 2*1000 (epoch 21) + 500 = 16900", 16900, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:23 slot: 300 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(23), intToConsensusSlotNumber(300))
    assertEquals("Absolute slot number should be 17700 => epoch=23 slot=300 fork=2 => (20*720) + 3*1000 (epoch 20-21-22) + 300 = 17700", 17700, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:23 slot: 1200 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(23), intToConsensusSlotNumber(1200))
    assertEquals("Absolute slot number should be 18600 => epoch=23 slot=1200 fork=2 => (20*720) + 3*1000 (epoch 20-21-22) + 1200 = 18600", 18600, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:24 slot: 600 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(24), intToConsensusSlotNumber(600))
    assertEquals("Absolute slot number should be 19200 => epoch=24 slot=600 fork=2 => (20*720) + 3*1000 (epoch 20-21-22) +1200(epoch 23) + 600 = 19200", 19200, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:25 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(25), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 19801 => epoch=25 slot=1 fork=3 => (20*720) + 3*1000 (epoch 20-21-22) + 2*1200 (epoch 23-24) + 1 = 19801", 19801, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:25 slot: 1200 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(25), intToConsensusSlotNumber(1200))
    assertEquals("Absolute slot number should be 21000 => epoch=25 slot=1200 fork=3 => (20*720) + 3*1000 (epoch 20-21-22) + 2*1200 (epoch 23-24) + 1200 = 21000", 21000, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:26 slot: 500 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(26), intToConsensusSlotNumber(500))
    assertEquals("Absolute slot number should be 21500 => epoch=26 slot=500 fork=3 => (20*720) + 3*1000 (epoch 20-21-22) + 3*1200 (epoch 23-24-25) + 500 = 21500", 21500, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:28 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(28), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 23401 => epoch=28 slot=1 fork=4 => (20*720) + 3*1000 (epoch 20-21-22) + 5*1200 (epoch 23-24-25-26-27) + 1 = 23401", 23401, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:28 slot: 500 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(28), intToConsensusSlotNumber(500))
    assertEquals("Absolute slot number should be 23900 => epoch=28 slot=500 fork=4 => (20*720) + 3*1000 (epoch 20-21-22) + 5*1200 (epoch 23-24-25-26-27) + 500 = 23900", 23900, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

    ///////////////////////  Test with block at epoch:29 slot: 400 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(29), intToConsensusSlotNumber(400))
    assertEquals("Absolute slot number should be 24300 => epoch=29 slot=400 fork=4 => (20*720) + 3*1000 (epoch 20-21-22) + 5*1200 (epoch 23-24-25-26-27) + 500 (epoch 28) + 400 = 24300", 24300, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp, blockTs))

  }

  @Test
  def checkSlotAndEpoch(): Unit = {
    val consensusSlotsInEpoch = 100
    val sidechainGenesisBlockTimestamp = 1990
    val consensusSecondsInSlot = 10
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(consensusSlotsInEpoch, consensusSecondsInSlot)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)
    ))

    assertEquals(" Seconds in epoch shall be as expected", 1000, TimeToEpochUtils.epochInSeconds(consensusSecondsInSlot, consensusSlotsInEpoch))
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
    val sidechainGenesisBlockTimestamp = 61
    val consensusSecondsInSlot = 3
    val consensusSlotsInEpoch = 8
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(0), Seq(8), Seq(3)), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(consensusSlotsInEpoch, consensusSecondsInSlot)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)
    ))

    assertEquals(" Seconds in epoch shall be as expected", 24, TimeToEpochUtils.epochInSeconds(consensusSecondsInSlot, consensusSlotsInEpoch))
    checkSlotAndEpoch(90, 1, 3)
    assertEquals(1, TimeToEpochUtils.secondsRemainingInSlot(params.sidechainGenesisBlockTimestamp,90))
    checkSlotAndEpoch(91, 2, 3)
    assertEquals(3, TimeToEpochUtils.secondsRemainingInSlot(params.sidechainGenesisBlockTimestamp,91))
    checkSlotAndEpoch(92, 2, 3)
    assertEquals(2, TimeToEpochUtils.secondsRemainingInSlot(params.sidechainGenesisBlockTimestamp,92))
    checkSlotAndEpoch(93, 2, 3)
    assertEquals(1, TimeToEpochUtils.secondsRemainingInSlot(params.sidechainGenesisBlockTimestamp,93))
    checkSlotAndEpoch(94, 3, 3)
    assertEquals(3, TimeToEpochUtils.secondsRemainingInSlot(params.sidechainGenesisBlockTimestamp,94))
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectEpoch(): Unit = {
    val sidechainGenesisBlockTimestamp = 2000
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp)

    TimeToEpochUtils.timeStampToEpochNumber(params.sidechainGenesisBlockTimestamp, 1999)
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectSlot(): Unit = {
    val sidechainGenesisBlockTimestamp = 6000
    val consensusSecondsInSlot = 10
    val consensusSlotsInEpoch = 100

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(consensusSlotsInEpoch, consensusSecondsInSlot)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp)
    ))

    TimeToEpochUtils.timeStampToSlotNumber(sidechainGenesisBlockTimestamp, -5)
  }
}

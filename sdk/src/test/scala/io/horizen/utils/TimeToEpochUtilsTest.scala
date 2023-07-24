package io.horizen.utils

import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion1}
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.consensus.{ConsensusEpochAndSlot, ConsensusParamsUtil, intToConsensusEpochNumber, intToConsensusSlotNumber}
import io.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import io.horizen.fork.{ConsensusParamsFork, CustomForkConfiguratorWithConsensusParamsFork, ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.params.NetworkParams
import io.horizen.proposition.SchnorrProposition
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.util.{ModifierId, bytesToId}
import sparkz.core.block.Block

import java.math.BigInteger

class TimeToEpochUtilsTest extends JUnitSuite {

  case class StubbedNetParams(override val sidechainGenesisBlockTimestamp: Block.Timestamp,
                              override val consensusSecondsInSlot: Int) extends NetworkParams {
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

  val defaultConsensusFork = ConsensusParamsFork.DefaultConsensusParamsFork

  private def checkSlotAndEpoch(timeStamp: Block.Timestamp,
                                expectedSlot: Int,
                                expectedEpoch: Int)(implicit params: StubbedNetParams): Unit = {
    assertEquals("Epoch shall be as expected", expectedEpoch, TimeToEpochUtils.timeStampToEpochNumber(params, timeStamp))
    assertEquals("Slot shall be as expected", expectedSlot, TimeToEpochUtils.timeStampToSlotNumber(params, timeStamp))
    val expectedAbsoluteSlot = expectedEpoch * defaultConsensusFork.consensusSlotsInEpoch + expectedSlot
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
  def generateAndValidateTimestampForDifferentEpochWithForks(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSecondsInSlot = 12
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(20,23), Seq(1000,1200)), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (20, new ConsensusParamsFork(1000)),
      (23, new ConsensusParamsFork(1200)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(23), intToConsensusSlotNumber(1))
    ))


    var old_ts: Long = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    var ts: Long = old_ts
    var ts_diff: Long = 0
    var consensusEpochAndSlot = ConsensusEpochAndSlot(intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    for (i <- 0 to 19) {
      for (y <- 1 to 720) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        if (i > 1) {
          assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(params, ts), i)
          assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(params, ts), y)
          consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, ts)
          assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        }
        old_ts = ts
      }
    }
    for (i <- 20 to 22) {
      for (y <- 1 to 1000) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(params, ts), i)
        assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(params, ts), y)
        consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, ts)
        assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        old_ts = ts
      }
    }
    for (i <- 23 to 25) {
      for (y <- 1 to 1200) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(params, ts), i)
        assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(params, ts), y)
        consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, ts)
        assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        old_ts = ts
      }
    }
  }

  @Test
  def generateAndValidateTimestampForDifferentEpochNoForks(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSecondsInSlot = 12
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)
    ))

    var old_ts: Long = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    var ts: Long = old_ts
    var ts_diff: Long = 0
    var consensusEpochAndSlot = ConsensusEpochAndSlot(intToConsensusEpochNumber(0), intToConsensusSlotNumber(0))
    for (i <- 0 to 25) {
      for (y <- 1 to 720) {
        ts = TimeToEpochUtils.getTimeStampForEpochAndSlot(params,  intToConsensusEpochNumber(i), intToConsensusSlotNumber(y))
        ts_diff = ts - old_ts
        assertEquals(f"The Timestamps should differ of ${consensusSecondsInSlot}", ts_diff, consensusSecondsInSlot)
        if (i > 1) {
          assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(params, ts), i)
          assertEquals("The recalculated epoch must be coherent", TimeToEpochUtils.timeStampToEpochNumber(params, ts), i)
          assertEquals("The recalculated slot must be coherent", TimeToEpochUtils.timeStampToSlotNumber(params, ts), y)
          consensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, ts)
          assertTrue("The recalculated epoch and slot must be coherent", consensusEpochAndSlot.epochNumber == i && consensusEpochAndSlot.slotNumber == y)
        }
        old_ts = ts
      }
    }
  }

  @Test
  def timestampToAbsoluteNumberNoForksTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSecondsInSlot = 12
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)
    ))

    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Absolute slot number should be 1440 => epoch=1 slot=720 fork=0 => 1*720 + 720 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with block at epoch:2 slot: 0 /////////////////
    var blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(2), intToConsensusSlotNumber(0))
    assertEquals("Absolute slot number should be 1440 => epoch=2 slot=0 fork=0 => (2*720) + 0 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(2), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 1441 => epoch=2 slot=1 fork=0 => (2*720) + 1 = 1441", 1441, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(2), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 2160 => epoch=2 slot=720 fork=0 => (2*720) + 720 = 1441", 2160, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 100 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(100))
    assertEquals("Absolute slot number should be 14500 => epoch=20 slot=100 fork=0 => (20*720) + 100 = 14500", 14500, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 15120 => epoch=20 slot=720 fork=0 => (20*720) + 720 = 15120", 15120, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

  }

  @Test
  def timestampToAbsoluteNumberWithForksTest(): Unit = {
    val sidechainGenesisBlockTimestamp = 10000
    val consensusSecondsInSlot = 12
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(20,23), Seq(1000,1200)), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, defaultConsensusFork),
      (20, new ConsensusParamsFork(1000)),
      (23, new ConsensusParamsFork(1200)),
    ))


    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(1)),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(23), intToConsensusSlotNumber(1))
    ))


    ///////////////////////  Test with genesis block /////////////////
    assertEquals("Absolute slot number should be 1440 => epoch=1 slot=720 fork=0 => 1*720 + 720 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, sidechainGenesisBlockTimestamp))

    ///////////////////////  Test with block at epoch:2 slot: 0 /////////////////
    var blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params,  intToConsensusEpochNumber(2), intToConsensusSlotNumber(0))
    assertEquals("Absolute slot number should be 1440 => epoch=2 slot=0 fork=0 => (2*720) + 0 = 1440", 1440, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(2), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 1441 => epoch=2 slot=1 fork=0 => (2*720) + 1 = 1441", 1441, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:2 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(2), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 2160 => epoch=2 slot=720 fork=0 => (2*720) + 720 = 1441", 2160, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:19 slot: 720 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(19), intToConsensusSlotNumber(720))
    assertEquals("Absolute slot number should be 14400 => epoch=19 slot=720 fork=0 => (19*720) + 720 = 14400", 14400, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 0 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(0))
    assertEquals("Absolute slot number should be 14400 => epoch=20 slot=0 fork=0 => (20*720) + 0 = 14400", 14400, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 1 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(1))
    assertEquals("Absolute slot number should be 14400 => epoch=20 slot=1 fork=1 => (20*720) + 1 = 14401", 14401, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:20 slot: 721 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(20), intToConsensusSlotNumber(721))
    assertEquals("Absolute slot number should be 15121 => epoch=20 slot=721 fork=1 => (20*720) + 721 = 15121", 15121, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:22 slot: 500 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(22), intToConsensusSlotNumber(500))
    assertEquals("Absolute slot number should be 16900 => epoch=22 slot=500 fork=1 => (20*720) + 2*1000 (epoch 21) + 500 = 16900", 16900, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:23 slot: 300 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(23), intToConsensusSlotNumber(300))
    assertEquals("Absolute slot number should be 17700 => epoch=23 slot=300 fork=1 => (20*720) + 3*1000 (epoch 20-21-22) + 300 = 17700", 17700, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:23 slot: 1200 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(23), intToConsensusSlotNumber(1200))
    assertEquals("Absolute slot number should be 18600 => epoch=23 slot=1200 fork=1 => (20*720) + 3*1000 (epoch 20-21-22) + 1200 = 18600", 18600, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

    ///////////////////////  Test with block at epoch:24 slot: 600 /////////////////
    blockTs = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(24), intToConsensusSlotNumber(600))
    assertEquals("Absolute slot number should be 19200 => epoch=24 slot=600 fork=1 => (20*720) + 4*1000 (epoch 20-21-22-23) + 600 = 19200", 19200, TimeToEpochUtils.timeStampToAbsoluteSlotNumber(params, blockTs))

  }

  @Test
  def checkSlotAndEpoch(): Unit = {
    val consensusSlotsInEpoch = 100
    val sidechainGenesisBlockTimestamp = 1990
    val consensusSecondsInSlot = 10
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(consensusSlotsInEpoch)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)
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
    val sidechainGenesisBlockTimestamp = 61
    val consensusSecondsInSlot = 3
    val consensusSlotsInEpoch = 8
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(consensusSlotsInEpoch)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)
    ))

    assertEquals(" Seconds in epoch shall be as expected", 24, TimeToEpochUtils.epochInSeconds(params, consensusSlotsInEpoch))
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
    val sidechainGenesisBlockTimestamp = 2000
    val consensusSecondsInSlot = 10
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    TimeToEpochUtils.timeStampToEpochNumber(params, 1999)
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectSlot(): Unit = {
    val sidechainGenesisBlockTimestamp = 6000
    val consensusSecondsInSlot = 10
    val consensusSlotsInEpoch = 100
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp = sidechainGenesisBlockTimestamp, consensusSecondsInSlot = consensusSecondsInSlot)

    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq()), "regtest")
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(consensusSlotsInEpoch)),
    ))

    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)
    ))

    TimeToEpochUtils.timeStampToSlotNumber(params, -5)
  }
}

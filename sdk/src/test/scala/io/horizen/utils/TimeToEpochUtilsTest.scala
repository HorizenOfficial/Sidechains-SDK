package io.horizen.utils

import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion1}
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.consensus.{intToConsensusEpochNumber, intToConsensusSlotNumber}
import io.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import io.horizen.params.NetworkParams
import io.horizen.proposition.SchnorrProposition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.util.{ModifierId, bytesToId}
import sparkz.core.block.Block

import java.math.BigInteger

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
    override val sc2ScProvingKeyFilePath: Option[String] = None
    override val sc2ScVerificationKeyFilePath: Option[String] = None
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
  def checkSlotAndEpoch(): Unit = {
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = 1990, consensusSecondsInSlot = 10, consensusSlotsInEpoch = 100)

    assertEquals(" Seconds in epoch shall be as expected", 1000, TimeToEpochUtils.epochInSeconds(params))
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
    implicit val params: StubbedNetParams = StubbedNetParams(sidechainGenesisBlockTimestamp = 61, consensusSecondsInSlot = 3, consensusSlotsInEpoch = 8)

    assertEquals(" Seconds in epoch shall be as expected", 24, TimeToEpochUtils.epochInSeconds(params))
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

    TimeToEpochUtils.timeStampToSlotNumber(params, -5)
  }

  @Test
  def testTimestampToSlotEpochNumber(): Unit = {
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp = 61, consensusSecondsInSlot = 3, consensusSlotsInEpoch = 8)

    val epochNumber = 345
    val slotNumber = 4
    val timestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    assertEquals(slotNumber, TimeToEpochUtils.timeStampToSlotNumber(params, timestamp))
  }
}

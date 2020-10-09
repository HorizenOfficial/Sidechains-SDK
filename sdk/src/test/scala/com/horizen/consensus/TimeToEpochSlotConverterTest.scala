package com.horizen.consensus

import java.math.BigInteger

import com.horizen.params.NetworkParams
import com.horizen.proposition.SchnorrProposition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.block.Block
import scorex.util.{ModifierId, bytesToId}

class TimeToEpochSlotConverterTest extends JUnitSuite {

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
    override val EquihashVarIntLength: Int = 3
    override val EquihashSolutionLength: Int = 1344
    override val withdrawalEpochLength: Int = 100
    override val powLimit: BigInteger = new BigInteger("07ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
    override val nPowAveragingWindow: Int = 17
    override val nPowMaxAdjustDown: Int = 32 // 32% adjustment down
    override val nPowMaxAdjustUp: Int = 16 // 16% adjustment up
    override val nPowTargetSpacing: Int = 150 // 2.5 * 60
    override val signersPublicKeys: Seq[SchnorrProposition] = Seq()
    override val signersThreshold: Int = 0
    override val provingKeyFilePath: String = ""
    override val verificationKeyFilePath: String = ""
    override val calculatedSysDataConstant: Array[Byte] = Array()
    override val closedBoxesMerkleTreeStatePath: String = ""
    override val closedBoxesMerkleTreeDbPath: String = ""
    override val closedBoxesMerkleTreeCachePath: String = ""
  }

  class HistoryStub(sidechainGenesisBlockTimestamp: Block.Timestamp,
                    consensusSecondsInSlot: Int,
                    consensusSlotsInEpoch: Int) {
    val params = StubbedNetParams(sidechainGenesisBlockTimestamp, consensusSecondsInSlot, consensusSlotsInEpoch)
  }

  private def checkSlotAndEpoch(timeStamp: Block.Timestamp,
                                expectedSlot: Int,
                                expectedEpoch: Int)(implicit converter: TimeToEpochSlotConverter): Unit = {
    assertEquals("Epoch shall be as expected", expectedEpoch, converter.timeStampToEpochNumber(timeStamp))
    assertEquals("Slot shall be as expected", expectedSlot, converter.timeStampToSlotNumber(timeStamp))
  }

  @Test
  def checkSlotAndEpoch(): Unit = {
    implicit val converter: TimeToEpochSlotConverter =
      new HistoryStub(sidechainGenesisBlockTimestamp = 1990, consensusSecondsInSlot = 10, consensusSlotsInEpoch = 100)
        with TimeToEpochSlotConverter

    assertEquals(" Seconds in epoch shall be as expected", 1000, converter.epochInSeconds)
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
    implicit val converter2: TimeToEpochSlotConverter =
      new HistoryStub(sidechainGenesisBlockTimestamp = 61, consensusSecondsInSlot = 3, consensusSlotsInEpoch = 8)
        with TimeToEpochSlotConverter

    assertEquals(" Seconds in epoch shall be as expected", 24, converter2.epochInSeconds)
    checkSlotAndEpoch(90, 1, 3)
    checkSlotAndEpoch(91, 2, 3)
    checkSlotAndEpoch(92, 2, 3)
    checkSlotAndEpoch(93, 2, 3)
    checkSlotAndEpoch(94, 3, 3)
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectEpoch(): Unit = {
    val converter: TimeToEpochSlotConverter =
      new HistoryStub(sidechainGenesisBlockTimestamp = 2000, consensusSecondsInSlot = 10, consensusSlotsInEpoch = 100)
        with TimeToEpochSlotConverter

    converter.timeStampToEpochNumber(1999)
  }

  @Test(expected = classOf[java.lang.IllegalArgumentException])
  def checkIncorrectSlot(): Unit = {
    val converter: TimeToEpochSlotConverter =
      new HistoryStub(6000, 10, 100)
        with TimeToEpochSlotConverter

    converter.timeStampToSlotNumber(-5)
  }

  @Test
  def testTimestampToSlotEpochNumber(): Unit = {
    val converter = new HistoryStub(sidechainGenesisBlockTimestamp = 61, consensusSecondsInSlot = 3, consensusSlotsInEpoch = 8)
      with TimeToEpochSlotConverter

    val epochNumber = 345
    val slotNumber = 4
    val timestamp = converter.getTimeStampForEpochAndSlot(intToConsensusEpochNumber(epochNumber), intToConsensusSlotNumber(slotNumber))

    assertEquals(epochNumber, converter.timeStampToEpochNumber(timestamp))
    assertEquals(slotNumber, converter.timeStampToSlotNumber(timestamp))
  }
}

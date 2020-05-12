package com.horizen.validation

import com.horizen.SidechainHistory
import com.horizen.block.{Ommer, SidechainBlock, SidechainBlockHeader}
import com.horizen.consensus._
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture, TransactionFixture}
import com.horizen.params.{MainNetParams, NetworkParams}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, assertArrayEquals, fail => jFail}
import scorex.util.ModifierId

import scala.util.{Failure, Success, Try}

class ConsensusValidatorOmmersTest
  extends JUnitSuite
    with MockitoSugar
  with CompanionsFixture
  with TransactionFixture
  with SidechainBlockFixture{

  val consensusValidator = new ConsensusValidator {
    // always successful
    override def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, message: VrfMessage): Unit = {}
    // always successful
    override private[horizen] def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo, message: VrfMessage): Unit = {}
  }


  @Test
  def emptyOmmersValidation(): Unit = {
    // Mock history
    val history = mockHistory()

    // Mock block with no ommers
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    Mockito.when(verifiedBlock.ommers).thenReturn(Seq())

    // Mock other data
    val currentFullConsensusEpochInfo: FullConsensusEpochInfo = mock[FullConsensusEpochInfo]
    val previousFullConsensusEpochInfo: FullConsensusEpochInfo = mock[FullConsensusEpochInfo]

    Try {
      consensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
    } match {
      case Success(_) =>
      case Failure(e) => throw e //jFail("Block with no ommers expected to be Valid.")
    }
  }

  @Test
  def sameEpochOmmersValidation(): Unit = {
    // Mock history
    val history = mockHistory()
    // Mock other data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](32)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(ConsensusNonce @@ currentEpochNonceBytes))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](32)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(ConsensusNonce @@ previousEpochNonceBytes))

    // Test 1: Valid Ommers in correct order from the same epoch as VerifiedBlock
    // Mock ommers
    var ommers: Seq[Ommer] = Seq(
      getMockedOmmer(history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 7)),
      getMockedOmmer(history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 8))
    )

    // Mock block with ommers
    val verifiedBlockTimestamp = history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 10)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)

    val currentEpochConsensusValidator = new ConsensusValidator {
      override def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, message: VrfMessage): Unit = {
        val slot = history.timeStampToSlotNumber(header.timestamp)
        val expectedVrfMessage = buildVrfMessage(slot, currentFullConsensusEpochInfo.nonceConsensusEpochInfo)
        assertArrayEquals("Different vrf message expected", expectedVrfMessage, message)
      }
      override private[horizen] def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo, message: VrfMessage): Unit = {
        assertEquals("Different stakeConsensusEpochInfo expected", currentFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
      }
    }

    Try {
      currentEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
    } match {
      case Success(_) =>
      case Failure(e) => throw e //jFail("Block with no ommers expected to be Valid.")
    }


    // Test 2: Same as above, but Ommers contains invalid forger box data
    val fbException = new Exception("ForgerBoxException")
    val forgerBoxFailConsensusValidator = new ConsensusValidator {
      // always successful
      override def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, message: VrfMessage): Unit = {}
      // always fail
      override private[horizen] def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo, message: VrfMessage): Unit = throw fbException
    }

    Try {
      forgerBoxFailConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
    } match {
      case Success(_) => jFail("Block with no ommers expected to be invalid.")
      case Failure(e) => assertEquals("Different exception expected.", fbException, e)
    }


    // Test 3: Same as above, but Ommers contains invalid VRF data
    val vrfException = new Exception("VRFException")
    val vrfFailConsensusValidator = new ConsensusValidator {
      // always fail
      override def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, message: VrfMessage): Unit = throw vrfException
      // always successful
      override private[horizen] def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo, message: VrfMessage): Unit = {}
    }

    Try {
      vrfFailConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
    } match {
      case Success(_) => jFail("Block with no ommers expected to be invalid.")
      case Failure(e) => assertEquals("Different exception expected.", vrfException, e)
    }
  }

  @Test
  def previousEpochOmmersValidation(): Unit = {
    val verifiedBlockId: ModifierId = getRandomBlockId(1000L)

    // Mock other data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](32)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(ConsensusNonce @@ currentEpochNonceBytes))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](32)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(ConsensusNonce @@ previousEpochNonceBytes))

    // Mock history
    val history = mockHistory()

    // Test 1: Valid Ommers in correct order from the previous epoch as VerifiedBlock
    // Mock ommers
    val ommers: Seq[Ommer] = Seq(
      getMockedOmmer(history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 20)),
      getMockedOmmer(history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 21))
    )

    // Mock block with ommers
    val verifiedBlockTimestamp = history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 10)
    var verifiedBlock: SidechainBlock = mock[SidechainBlock]
    Mockito.when(verifiedBlock.id).thenReturn(verifiedBlockId)
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)

    val previousEpochConsensusValidator = new ConsensusValidator {
      override def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, message: VrfMessage): Unit = {
        val slot = history.timeStampToSlotNumber(header.timestamp)
        val expectedVrfMessage = buildVrfMessage(slot, previousFullConsensusEpochInfo.nonceConsensusEpochInfo)
        assertArrayEquals("Different vrf message expected", expectedVrfMessage, message)
      }
      override private[horizen] def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo, message: VrfMessage): Unit = {
        assertEquals("Different stakeConsensusEpochInfo expected", previousFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
      }
    }

    Try {
      previousEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
    } match {
      case Success(_) =>
      case Failure(e) => throw e //jFail("Block with no ommers expected to be Valid.")
    }
  }

  @Test
  def switchingEpochOmmersValidation(): Unit = {
    // Case when some ommers are part of previous epoch is not supported yet - so exception expected
    val verifiedBlockId: ModifierId = getRandomBlockId(1000L)

    // Mock other data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](32)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(ConsensusNonce @@ currentEpochNonceBytes))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](32)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(ConsensusNonce @@ previousEpochNonceBytes))

    // Mock history
    val history = mockHistory()

    // Test 1: Valid Ommers in correct order from the previous epoch as VerifiedBlock
    // Mock ommers
    val ommers: Seq[Ommer] = Seq(
      getMockedOmmer(history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 20)),
      getMockedOmmer(history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 9))
    )

    // Mock block with ommers
    val verifiedBlockTimestamp = history.getTimeStampForEpochAndSlot(ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 10)
    var verifiedBlock: SidechainBlock = mock[SidechainBlock]
    Mockito.when(verifiedBlock.id).thenReturn(verifiedBlockId)
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)

    Try {
      consensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
    } match {
      case Success(_) => jFail("Block with ommers form different epochs expected to be Invalid.")
      case Failure(e) => e match {
        case _: IllegalStateException =>
        case otherException => throw otherException //jFail("Different exception expected")
      }
    }
  }

  private def getMockedOmmer(timestamp: Long): Ommer = {
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(timestamp)

    Ommer(header, None, Seq(), Seq())
  }

  private def mockHistory(): SidechainHistory = {
    val params: NetworkParams = MainNetParams()
    // Because TimeToEpochSlotConverter is a trait, we need to do this dirty stuff to use its methods as a part of mocked SidechainHistory
    class TimeToEpochSlotConverterImpl(val params: NetworkParams) extends TimeToEpochSlotConverter
    val converter = new TimeToEpochSlotConverterImpl(params)

    val history: SidechainHistory = mock[SidechainHistory]
    Mockito.when(history.timeStampToEpochNumber(ArgumentMatchers.any[Long])).thenAnswer(answer => {
      converter.timeStampToEpochNumber(answer.getArgument(0))
    })

    Mockito.when(history.timeStampToSlotNumber(ArgumentMatchers.any[Long])).thenAnswer(answer => {
      converter.timeStampToSlotNumber(answer.getArgument(0))
    })

    Mockito.when(history.timeStampToAbsoluteSlotNumber(ArgumentMatchers.any[Long])).thenAnswer(answer => {
      converter.timeStampToAbsoluteSlotNumber(answer.getArgument(0))
    })

    Mockito.when(history.getTimeStampForEpochAndSlot(ArgumentMatchers.any[ConsensusEpochNumber], ArgumentMatchers.any[ConsensusSlotNumber])).thenAnswer(answer => {
      converter.getTimeStampForEpochAndSlot(answer.getArgument(0), answer.getArgument(1))
    })

    Mockito.when(history.params).thenReturn(params)

    history
  }
}

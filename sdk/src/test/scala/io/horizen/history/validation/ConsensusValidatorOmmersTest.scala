package io.horizen.history.validation

import java.util.Random
import io.horizen.SidechainTypes
import io.horizen.block.{Ommer, SidechainBlockHeaderBase}
import io.horizen.chain.SidechainBlockInfo
import io.horizen.consensus._
import io.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture, TransactionFixture}
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo, ForkManager, ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.utils.TimeToEpochUtils
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.storage.SidechainHistoryStorage
import io.horizen.vrf.VrfOutput
import org.junit.Assert.{assertEquals, fail => jFail}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.util.ModifierId

import scala.util.{Failure, Success, Try}

class ConsensusValidatorOmmersTest
  extends JUnitSuite
    with MockitoSugar
    with CompanionsFixture
    with TransactionFixture
    with SidechainBlockFixture
    with TimeProviderFixture {

  type BoxConsensusValidator = ConsensusValidator[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage, SidechainHistory]

  val consensusValidator: BoxConsensusValidator = new BoxConsensusValidator(timeProvider) {
    // always successful
    override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {}
  }

  @Before
  def init(): Unit = {
    ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
  }

  @Test
  def emptyOmmersValidation(): Unit = {
    // Mock history
    val history = mockHistory()

    // Mock block with no ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    Mockito.when(verifiedBlock.ommers).thenReturn(Seq())

    // Mock other data
    val currentFullConsensusEpochInfo: FullConsensusEpochInfo = mock[FullConsensusEpochInfo]
    val previousFullConsensusEpochInfo: FullConsensusEpochInfo = mock[FullConsensusEpochInfo]

    Try {
      consensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with no ommers expected to be Valid, instead exception: ${e.getMessage}")
    }
  }

  @Test
  def sameEpochOmmersValidation(): Unit = {
    // Mock history
    val history = mockHistory()

    // Mock other data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(currentEpochNonceBytes)))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(previousEpochNonceBytes)))

    // Test 1: Valid Ommers in correct order from the same epoch as VerifiedBlock
    // Mock ommers
    val ommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 7)),
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 8))
    )

    // Mock block with ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 11)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)


    val currentEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        assertEquals("Different stakeConsensusEpochInfo expected", currentFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      currentEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from the same epoch expected to be Valid, instead exception: ${e.getMessage}")
    }


    // Test 2: Same as above, but Ommers contains invalid forging stake info data
    val fsException = new Exception("ForgingStakeException")
    val forgingStakeFailConsensusValidator = new BoxConsensusValidator(timeProvider) {
      // always fail
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = throw fsException
    }

    Try {
      forgingStakeFailConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) => jFail("Block with no ommers expected to be invalid.")
      case Failure(e) => assertEquals("Different exception expected.", fsException, e)
    }


    // Test 3: Valid ommers with valid subommers in correct order from the same epoch as VerifiedBlock
    val ommersWithSubommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 9), ommers), // with subommers for 3/7, 3/8
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 10))
    )
    Mockito.when(verifiedBlock.ommers).thenReturn(ommersWithSubommers)

    Try {
      currentEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from the same epoch expected to be Valid, instead exception: ${e.getMessage}")
    }
  }

  @Test
  def previousEpochOmmersValidation(): Unit = {
    val verifiedBlockId: ModifierId = getRandomBlockId(1000L)

    // Mock other data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(currentEpochNonceBytes)))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(previousEpochNonceBytes)))

    // Mock history
    val history = mockHistory()

    // Test 1: Valid Ommers in correct order from the previous epoch to VerifiedBlock
    // Mock ommers
    val ommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 20)),
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 21))
    )

    // Mock block with ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 3, ConsensusSlotNumber @@ 10)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    Mockito.when(verifiedBlock.id).thenReturn(verifiedBlockId)
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)

    val previousEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        assertEquals("Different stakeConsensusEpochInfo expected", previousFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      previousEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from previous epoch only expected to be Valid, instead exception: ${e.getMessage}")
    }


    // Test 2: Valid ommers with valid subommers in correct order from previous epoch to VerifiedBlock
    val anotherOmmers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 44)),
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 46))
    )
    val ommersWithSubommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 40), ommers), // with subommers for 2/20, 2/21
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ 2, ConsensusSlotNumber @@ 50), anotherOmmers) // with subommers for 2/44, 2/46
    )
    Mockito.when(verifiedBlock.ommers).thenReturn(ommersWithSubommers)

    Try {
      previousEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from the same epoch expected to be Valid, instead exception: ${e.getMessage}")
    }
  }

  @Test
  def switchingEpochOmmersValidation(): Unit = {
    // Mock Consensus epoch info data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(currentEpochNonceBytes)))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(previousEpochNonceBytes)))

    val switchedOmmersCurrentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(switchedOmmersCurrentEpochNonceBytes)
    val switchedOmmersFullConsensusEpochInfo = FullConsensusEpochInfo(currentFullConsensusEpochInfo.stakeConsensusEpochInfo,
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(switchedOmmersCurrentEpochNonceBytes)))


    /* Test 1: Valid Ommers in correct order from the previous and current epoch as VerifiedBlock
      Notation <epoch_number>/<slot_number>
      Slots in epoch:   6
      Block slots number:   2/2 - 3/6
                                   |
      Ommers slots:   [2/5    ,   3/1   ,   3/5]
      Ommer 2/5 is in `quite` slots, so for 3/6 block and 3/1 ommer nonce will be the same.
    */


    // Mock history
    val slotsInEpoch: Int = 6
    var history = mockHistory(slotsInEpoch)

    val previousEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 2
    val currentEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 3

    // Mock ommers
    val ommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 5)), // quite slot - no impact on nonce calculation
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 1)),
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 5))
    )

    // Set initialNonceData (reverse order expected)
    val expectedInitialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = Seq(
      (generateDummyVrfOutput(ommers.head.header), ConsensusSlotNumber @@ 5)
    )

    // Mock block with ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 6)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)


    Mockito.when(history.calculateNonceForNonGenesisEpoch(
      ArgumentMatchers.any[ModifierId],
      ArgumentMatchers.any[SidechainBlockInfo],
      ArgumentMatchers.any[Seq[(VrfOutput, ConsensusSlotNumber)]])).thenAnswer(answer => {
      val lastBlockIdInEpoch: ModifierId = answer.getArgument(0)
      val lastBlockInfoInEpoch: SidechainBlockInfo = answer.getArgument(1)
      val initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = answer.getArgument(2)

      assertEquals("On calculate nonce: lastBlockIdInEpoch is different", parentId, lastBlockIdInEpoch)
      assertEquals("On calculate nonce: lastBlockInfoInEpoch is different", parentInfo, lastBlockInfoInEpoch)
      assertEquals("On calculate nonce: initialNonceData is different", expectedInitialNonceData, initialNonceData)

      // Return nonce same as current epoch nonce
      currentFullConsensusEpochInfo.nonceConsensusEpochInfo
    })

    var switchedEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, header.timestamp)
        epochAndSlot.epochNumber match {
          case `previousEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", previousFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case `currentEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", currentFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case epoch => jFail(s"Unknown epoch number: $epoch")
        }
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      switchedEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from both the same and previous epoch expected to be Valid, instead exception: ${e.getMessage}")
    }


    /* Test 2: Valid Ommers in correct order from the previous and current epoch as VerifiedBlock
       Notation <epoch_number>/<slot_number>
       Slots in epoch:   6
       Block slots number:   2/2 - 3/6
                                    |
       Ommers slots:   [2/3    ,   2/4   ,   3/5]
       Ommers 2/3;2/4 is in `active` slots for nonce calculation, so for block 3/6 and ommer 3/5 nonce will be different.
     */

    history = mockHistory(slotsInEpoch)

    val anotherOmmers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 3)), // active slot - has impact on nonce calculation
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 4)), // active slot - has impact on nonce calculation
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 5))
    )

    // Set initialNonceData (reverse order expected)
    val anotherExpectedInitialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = Seq(
      (generateDummyVrfOutput(anotherOmmers(1).header), ConsensusSlotNumber @@ 4),
      (generateDummyVrfOutput(anotherOmmers(0).header), ConsensusSlotNumber @@ 3)
    )

    Mockito.when(verifiedBlock.ommers).thenReturn(anotherOmmers)

    Mockito.when(history.calculateNonceForNonGenesisEpoch(
      ArgumentMatchers.any[ModifierId],
      ArgumentMatchers.any[SidechainBlockInfo],
      ArgumentMatchers.any[Seq[(VrfOutput, ConsensusSlotNumber)]])).thenAnswer(answer => {
      val lastBlockIdInEpoch: ModifierId = answer.getArgument(0)
      val lastBlockInfoInEpoch: SidechainBlockInfo = answer.getArgument(1)
      val initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = answer.getArgument(2)

      assertEquals("On calculate nonce: lastBlockIdInEpoch is different", parentId, lastBlockIdInEpoch)
      assertEquals("On calculate nonce: lastBlockInfoInEpoch is different", parentInfo, lastBlockInfoInEpoch)
      assertEquals("On calculate nonce: initialNonceData is different", anotherExpectedInitialNonceData, initialNonceData)

      // Return nonce same different from current epoch nonce
      switchedOmmersFullConsensusEpochInfo.nonceConsensusEpochInfo
    })

    switchedEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, header.timestamp)
        epochAndSlot.epochNumber match {
          case `previousEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", previousFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case `currentEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", switchedOmmersFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case epoch => jFail(s"Unknown epoch number: $epoch")
        }
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      switchedEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from both the same and previous epoch expected to be Valid, instead exception: ${e.getMessage}")
    }
  }

  @Test
  def switchingEpochOmmersValidationWithConsensusParamsForkActivation(): Unit = {
    // Mock Consensus epoch info data
    val postForkEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(postForkEpochNonceBytes)
    val postForkFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(postForkEpochNonceBytes)))

    val preForkEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(preForkEpochNonceBytes)
    val preForkFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(preForkEpochNonceBytes)))

    val switchedOmmersPostForkEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(switchedOmmersPostForkEpochNonceBytes)
    val switchedOmmersFullConsensusEpochInfo = FullConsensusEpochInfo(postForkFullConsensusEpochInfo.stakeConsensusEpochInfo,
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(switchedOmmersPostForkEpochNonceBytes)))


    /* Test 1: Valid Ommers in correct order from the previous and current epoch as VerifiedBlock
      Notation <epoch_number>/<slot_number>
      Slots in epoch:   6
      Block slots number:   2/2 - 3/6
                                   |
      Ommers slots:   [2/5    ,   3/1   ,   3/5]
      Ommer 2/5 is in `quite` slots, so for 3/6 block and 3/1 ommer nonce will be the same.
    */


    // Mock history
    val preForkEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 2
    val postForkEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 3

    val slotsInEpoch: Int = 6
    val slotsInEpochAfterFork: Int = 50
    val consensusParamsForkSeq = Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(slotsInEpoch)),
      ConsensusParamsForkInfo(postForkEpochNumber, new ConsensusParamsFork(slotsInEpochAfterFork))
    )

    var history = mockHistoryWithConsensusParameterForks(consensusParamsForkSeq)

    // Mock ommers
    val ommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 5)), // quite slot - no impact on nonce calculation
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 1)),
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 5))
    )

    // Set initialNonceData (reverse order expected)
    val expectedInitialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = Seq(
      (generateDummyVrfOutput(ommers.head.header), ConsensusSlotNumber @@ 5)
    )

    // Mock block with ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 6)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)


    Mockito.when(history.calculateNonceForNonGenesisEpoch(
      ArgumentMatchers.any[ModifierId],
      ArgumentMatchers.any[SidechainBlockInfo],
      ArgumentMatchers.any[Seq[(VrfOutput, ConsensusSlotNumber)]])).thenAnswer(answer => {
      val lastBlockIdInEpoch: ModifierId = answer.getArgument(0)
      val lastBlockInfoInEpoch: SidechainBlockInfo = answer.getArgument(1)
      val initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = answer.getArgument(2)

      assertEquals("On calculate nonce: lastBlockIdInEpoch is different", parentId, lastBlockIdInEpoch)
      assertEquals("On calculate nonce: lastBlockInfoInEpoch is different", parentInfo, lastBlockInfoInEpoch)
      assertEquals("On calculate nonce: initialNonceData is different", expectedInitialNonceData, initialNonceData)

      // Return nonce same as current epoch nonce
      postForkFullConsensusEpochInfo.nonceConsensusEpochInfo
    })

    var switchedEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, header.timestamp)
        epochAndSlot.epochNumber match {
          case `preForkEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", preForkFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case `postForkEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", postForkFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case epoch => jFail(s"Unknown epoch number: $epoch")
        }
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      switchedEpochConsensusValidator.verifyOmmers(verifiedBlock, postForkFullConsensusEpochInfo, Some(preForkFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from both the same and previous epoch expected to be Valid, instead exception: ${e.getMessage}")
    }


    /* Test 2: Valid Ommers in correct order from the previous and current epoch as VerifiedBlock
       Notation <epoch_number>/<slot_number>
       Slots in epoch:   6
       Block slots number:   2/2 - 3/6
                                    |
       Ommers slots:   [2/3    ,   2/4   ,   3/15]
       Ommers 2/3;2/4 is in `active` slots for nonce calculation, so for block 3/6 and ommer 3/15 nonce will be different.
     */

    history = mockHistoryWithConsensusParameterForks(consensusParamsForkSeq)

    val anotherOmmers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 3)), // active slot - has impact on nonce calculation
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 4)), // active slot - has impact on nonce calculation
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 15))
    )

    // Set initialNonceData (reverse order expected)
    val anotherExpectedInitialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = Seq(
      (generateDummyVrfOutput(anotherOmmers(1).header), ConsensusSlotNumber @@ 4),
      (generateDummyVrfOutput(anotherOmmers(0).header), ConsensusSlotNumber @@ 3)
    )

    Mockito.when(verifiedBlock.ommers).thenReturn(anotherOmmers)

    Mockito.when(history.calculateNonceForNonGenesisEpoch(
      ArgumentMatchers.any[ModifierId],
      ArgumentMatchers.any[SidechainBlockInfo],
      ArgumentMatchers.any[Seq[(VrfOutput, ConsensusSlotNumber)]])).thenAnswer(answer => {
      val lastBlockIdInEpoch: ModifierId = answer.getArgument(0)
      val lastBlockInfoInEpoch: SidechainBlockInfo = answer.getArgument(1)
      val initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = answer.getArgument(2)

      assertEquals("On calculate nonce: lastBlockIdInEpoch is different", parentId, lastBlockIdInEpoch)
      assertEquals("On calculate nonce: lastBlockInfoInEpoch is different", parentInfo, lastBlockInfoInEpoch)
      assertEquals("On calculate nonce: initialNonceData is different", anotherExpectedInitialNonceData, initialNonceData)

      // Return nonce same different from current epoch nonce
      switchedOmmersFullConsensusEpochInfo.nonceConsensusEpochInfo
    })

    switchedEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, header.timestamp)
        epochAndSlot.epochNumber match {
          case `preForkEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", preForkFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case `postForkEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", switchedOmmersFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case epoch => jFail(s"Unknown epoch number: $epoch")
        }
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      switchedEpochConsensusValidator.verifyOmmers(verifiedBlock, postForkFullConsensusEpochInfo, Some(preForkFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from both the same and previous epoch expected to be Valid, instead exception: ${e.getMessage}")
    }
  }

  @Test
  def switchingEpochOmmersWithSubOmmersValidation(): Unit = {
    // Mock Consensus epoch info data
    val currentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(currentEpochNonceBytes)
    val currentFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(currentEpochNonceBytes)))

    val previousEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(previousEpochNonceBytes)
    val previousFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(previousEpochNonceBytes)))

    val switchedOmmersCurrentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(switchedOmmersCurrentEpochNonceBytes)
    val switchedOmmersFullConsensusEpochInfo = FullConsensusEpochInfo(currentFullConsensusEpochInfo.stakeConsensusEpochInfo,
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(switchedOmmersCurrentEpochNonceBytes)))


    /* Test 1: Valid Ommers with subommers in correct order from the previous and current epoch as VerifiedBlock
       Notation <epoch_number>/<slot_number>
       Slots in epoch:   6
       Block slots number:   2/2 - 3/5
                                    |
       Ommers slots:   [2/4    ,   2/6   ,   3/4]
                         |          |         |
       Subommers slots:[2/3]      [2/5]  [3/2 , 3/3]
                                           |
       Subommers slots:                  [3/1]
       Ommer 2/3 is in `active` slots for nonce calculation, so for block 3/5 and ommers 3/1; 3/2; 3/3; 3/4 nonce will be different.
     */


    // Mock history
    val slotsInEpoch: Int = 6
    val history = mockHistory(slotsInEpoch)

    val previousEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 2
    val currentEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 3

    // Mock ommers
    val ommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 4),  // active slot - has impact on nonce calculation
        Seq(
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 3))
        )),

      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 6), // quite slot - no impact on nonce calculation
        Seq(
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, previousEpochNumber, ConsensusSlotNumber @@ 5))
        )),

      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 4),
        Seq(
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 2),
            Seq(
              getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 1))
            )),
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 3))
        ))
    )

    // Set initialNonceData (reverse order expected)
    val expectedInitialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = Seq(
      (generateDummyVrfOutput(ommers(1).header), ConsensusSlotNumber @@ 6),
      (generateDummyVrfOutput(ommers(0).header), ConsensusSlotNumber @@ 4)
    )

    // Mock block with ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, currentEpochNumber, ConsensusSlotNumber @@ 5)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)


    Mockito.when(history.calculateNonceForNonGenesisEpoch(
      ArgumentMatchers.any[ModifierId],
      ArgumentMatchers.any[SidechainBlockInfo],
      ArgumentMatchers.any[Seq[(VrfOutput, ConsensusSlotNumber)]])).thenAnswer(answer => {
      val lastBlockIdInEpoch: ModifierId = answer.getArgument(0)
      val lastBlockInfoInEpoch: SidechainBlockInfo = answer.getArgument(1)
      val initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = answer.getArgument(2)

      assertEquals("On calculate nonce: lastBlockIdInEpoch is different", parentId, lastBlockIdInEpoch)
      assertEquals("On calculate nonce: lastBlockInfoInEpoch is different", parentInfo, lastBlockInfoInEpoch)
      assertEquals("On calculate nonce: initialNonceData is different", expectedInitialNonceData, initialNonceData)

      // Return nonce same different from current epoch nonce
      switchedOmmersFullConsensusEpochInfo.nonceConsensusEpochInfo
    })

    val switchedEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, header.timestamp)
        epochAndSlot.epochNumber match {
          case `previousEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", previousFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case `currentEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", switchedOmmersFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case epoch => jFail(s"Unknown epoch number: $epoch")
        }
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      switchedEpochConsensusValidator.verifyOmmers(verifiedBlock, currentFullConsensusEpochInfo, Some(previousFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e // jFail(s"Block with ommers from both the same and previous epoch expected to be Valid, instead exception: ${e.getMessage}")
    }

  }

  @Test
  def switchingEpochOmmersWithSubOmmersValidationAfterConsensusParamsFork(): Unit = {
    // Mock Consensus epoch info data
    val postForkEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(postForkEpochNonceBytes)
    val postForkFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(postForkEpochNonceBytes)))

    val preForkEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(preForkEpochNonceBytes)
    val preForkFullConsensusEpochInfo = FullConsensusEpochInfo(mock[StakeConsensusEpochInfo],
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(preForkEpochNonceBytes)))

    val switchedOmmersCurrentEpochNonceBytes: Array[Byte] = new Array[Byte](8)
    scala.util.Random.nextBytes(switchedOmmersCurrentEpochNonceBytes)
    val switchedOmmersFullConsensusEpochInfo = FullConsensusEpochInfo(postForkFullConsensusEpochInfo.stakeConsensusEpochInfo,
      NonceConsensusEpochInfo(byteArrayToConsensusNonce(switchedOmmersCurrentEpochNonceBytes)))


    /* Test 1: Valid Ommers with subommers in correct order from the previous and current epoch as VerifiedBlock
       Notation <epoch_number>/<slot_number>
       Slots in epoch:   6
       Block slots number:   2/2 - 3/5
                                    |
       Ommers slots:   [2/4    ,   2/6   ,   3/15]
                         |          |         |
       Subommers slots:[2/3]      [2/5]  [3/2 , 3/3]
                                           |
       Subommers slots:                  [3/1]
       Ommer 2/3 is in `active` slots for nonce calculation, so for block 3/5 and ommers 3/1; 3/2; 3/3; 3/4 nonce will be different.
     */


    // Mock history

    val preForkEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 2
    val postForkEpochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 3

    val slotsInEpoch: Int = 6
    val slotsInEpochAfterFork: Int = 50
    val consensusParamsForkSeq = Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(slotsInEpoch)),
      ConsensusParamsForkInfo(postForkEpochNumber, new ConsensusParamsFork(slotsInEpochAfterFork))
    )

    val history = mockHistoryWithConsensusParameterForks(consensusParamsForkSeq)

    // Mock ommers
    val ommers: Seq[Ommer[SidechainBlockHeader]] = Seq(
      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 4), // active slot - has impact on nonce calculation
        Seq(
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 3))
        )),

      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 6), // quite slot - no impact on nonce calculation
        Seq(
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, preForkEpochNumber, ConsensusSlotNumber @@ 5))
        )),

      getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 15),
        Seq(
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 2),
            Seq(
              getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 1))
            )),
          getMockedOmmer(TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 3))
        ))
    )

    // Set initialNonceData (reverse order expected)
    val expectedInitialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = Seq(
      (generateDummyVrfOutput(ommers(1).header), ConsensusSlotNumber @@ 6),
      (generateDummyVrfOutput(ommers(0).header), ConsensusSlotNumber @@ 4)
    )

    // Mock block with ommers
    val parentId: ModifierId = getRandomBlockId()
    val parentInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
    val verifiedBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, postForkEpochNumber, ConsensusSlotNumber @@ 5)
    val verifiedBlock: SidechainBlock = mock[SidechainBlock]
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(verifiedBlockTimestamp)
    Mockito.when(verifiedBlock.header).thenReturn(header)
    Mockito.when(verifiedBlock.ommers).thenReturn(ommers)


    Mockito.when(history.calculateNonceForNonGenesisEpoch(
      ArgumentMatchers.any[ModifierId],
      ArgumentMatchers.any[SidechainBlockInfo],
      ArgumentMatchers.any[Seq[(VrfOutput, ConsensusSlotNumber)]])).thenAnswer(answer => {
      val lastBlockIdInEpoch: ModifierId = answer.getArgument(0)
      val lastBlockInfoInEpoch: SidechainBlockInfo = answer.getArgument(1)
      val initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)] = answer.getArgument(2)

      assertEquals("On calculate nonce: lastBlockIdInEpoch is different", parentId, lastBlockIdInEpoch)
      assertEquals("On calculate nonce: lastBlockInfoInEpoch is different", parentInfo, lastBlockInfoInEpoch)
      assertEquals("On calculate nonce: initialNonceData is different", expectedInitialNonceData, initialNonceData)

      // Return nonce same different from current epoch nonce
      switchedOmmersFullConsensusEpochInfo.nonceConsensusEpochInfo
    })

    val switchedEpochConsensusValidator = new BoxConsensusValidator(timeProvider) {
      override private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeaderBase, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput, percentageForkApplied: Boolean, activeSlotCoefficient: Double): Unit = {
        val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(history.params.sidechainGenesisBlockTimestamp, header.timestamp)
        epochAndSlot.epochNumber match {
          case `preForkEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", preForkFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case `postForkEpochNumber` => assertEquals("Different stakeConsensusEpochInfo expected", switchedOmmersFullConsensusEpochInfo.stakeConsensusEpochInfo, stakeConsensusEpochInfo)
          case epoch => jFail(s"Unknown epoch number: $epoch")
        }
        assertEquals("Different vrfOutput expected", generateDummyVrfOutput(header), vrfOutput)
      }
    }

    Try {
      switchedEpochConsensusValidator.verifyOmmers(verifiedBlock, postForkFullConsensusEpochInfo, Some(preForkFullConsensusEpochInfo), parentId, parentInfo, history, Seq())
    } match {
      case Success(_) =>
      case Failure(e) => throw e
    }

  }

  private def getMockedOmmer(timestamp: Long, subOmmers: Seq[Ommer[SidechainBlockHeader]] = Seq()): Ommer[SidechainBlockHeader] = {
    val header = mock[SidechainBlockHeader]
    Mockito.when(header.timestamp).thenReturn(timestamp)

    Ommer(header, None, Seq(), subOmmers)
  }

  private def generateDummyVrfOutput(ommerBlockHeader: SidechainBlockHeaderBase): VrfOutput = {
    val outputBytes = new Array[Byte](VrfOutput.OUTPUT_LENGTH)
    val rnd = new Random(ommerBlockHeader.timestamp)
    rnd.nextBytes(outputBytes)
    new VrfOutput(outputBytes)
  }

  private def mockHistory(slotsInEpoch: Int = 720): SidechainHistory = {
    val params: NetworkParams = MainNetParams()
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(slotsInEpoch)),
    ))
    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)))

    val history: SidechainHistory = mock[SidechainHistory]

    Mockito.when(history.params).thenReturn(params)

    Mockito.when(history.getVrfOutput(ArgumentMatchers.any[SidechainBlockHeader], ArgumentMatchers.any[NonceConsensusEpochInfo])).thenAnswer(answer => {
      val header: SidechainBlockHeader = answer.getArgument(0)
      Some(generateDummyVrfOutput(header))
    })

    history
  }

  private def mockHistoryWithConsensusParameterForks(consensusParamsFork: Seq[ConsensusParamsForkInfo]): SidechainHistory = {
    val params: NetworkParams = MainNetParams()

    ConsensusParamsUtil.setConsensusParamsForkActivation(consensusParamsFork)

    consensusParamsFork.foreach(paramsFork => {
      ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(
        Seq(
          TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp),
          TimeToEpochUtils.getTimeStampForEpochAndSlot(params.sidechainGenesisBlockTimestamp, ConsensusEpochNumber @@ paramsFork.activationEpoch, ConsensusSlotNumber @@ 0)
        )
      )
    })

    val history: SidechainHistory = mock[SidechainHistory]

    Mockito.when(history.params).thenReturn(params)

    Mockito.when(history.getVrfOutput(ArgumentMatchers.any[SidechainBlockHeader], ArgumentMatchers.any[NonceConsensusEpochInfo])).thenAnswer(answer => {
      val header: SidechainBlockHeader = answer.getArgument(0)
      Some(generateDummyVrfOutput(header))
    })

    history
  }
}

package com.horizen.validation
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{OmmersContainer, SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.consensus._
import com.horizen.vrf.VrfOutput
import scorex.core.block.Block
import scorex.util.{ScorexLogging, ModifierId}

import scala.util.Try

class ConsensusValidator extends HistoryBlockValidator with ScorexLogging {
  override def validate(block: SidechainBlock, history: SidechainHistory): Try[Unit] = Try {
    if (history.isGenesisBlock(block.id)) {
      validateGenesisBlock(block, history)
    }
    else {
      validateNonGenesisBlock(block, history)
    }
  }

  private def validateGenesisBlock(block: SidechainBlock, history: SidechainHistory): Unit = {
    if (block.timestamp != history.params.sidechainGenesisBlockTimestamp) {
      throw new IllegalArgumentException(s"Genesis block timestamp ${block.timestamp} is differ than expected timestamp from configuration ${history.params.sidechainGenesisBlockTimestamp}")
    }

    val vrfSignIsNotCorrect = false //@TODO we should call verifyVfr and verifyForgingStakeInfo with consensusEpochInfo calculated from SC creation TX and a constant for nonce
    if (vrfSignIsNotCorrect) {
      throw new IllegalArgumentException(s"Genesis block timestamp is not signed his own forger box")
    }
  }


  private def validateNonGenesisBlock(verifiedBlock: SidechainBlock, history: SidechainHistory): Unit = {
    val parentBlockInfo: SidechainBlockInfo = history.blockInfoById(verifiedBlock.parentId)
    verifyTimestamp(verifiedBlock.timestamp, parentBlockInfo.timestamp, history)

    val currentConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(verifiedBlock.timestamp, verifiedBlock.parentId)

    val vrfOutput: VrfOutput = history.getVrfOutput(verifiedBlock.header, currentConsensusEpochInfo.nonceConsensusEpochInfo)
      .getOrElse(throw new IllegalStateException(s"VRF check for block ${verifiedBlock.id} had been failed"))

    verifyForgingStakeInfo(verifiedBlock.header, currentConsensusEpochInfo.stakeConsensusEpochInfo, vrfOutput)

    val lastBlockInPreviousConsensusEpochInfo: SidechainBlockInfo = history.blockInfoById(history.getLastBlockInPreviousConsensusEpoch(verifiedBlock.timestamp, verifiedBlock.parentId))
    val previousFullConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(lastBlockInPreviousConsensusEpochInfo.timestamp, lastBlockInPreviousConsensusEpochInfo.parentId)
    verifyOmmers(verifiedBlock, currentConsensusEpochInfo, Some(previousFullConsensusEpochInfo), verifiedBlock.parentId, parentBlockInfo, history, Seq())

    verifyTimestampInFuture(verifiedBlock.timestamp, history)
  }

  private def verifyTimestamp(verifiedBlockTimestamp: Block.Timestamp, parentBlockTimestamp: Block.Timestamp, timeConverter: TimeToEpochSlotConverter): Unit = {
    if (verifiedBlockTimestamp < parentBlockTimestamp) throw new IllegalArgumentException("Block had been generated before parent block had been generated")

    val absoluteSlotNumberForVerifiedBlock = timeConverter.timeStampToAbsoluteSlotNumber(verifiedBlockTimestamp)
    val absoluteSlotNumberForParentBlock = timeConverter.timeStampToAbsoluteSlotNumber(parentBlockTimestamp)
    if (absoluteSlotNumberForVerifiedBlock <= absoluteSlotNumberForParentBlock) throw new IllegalArgumentException("Block absolute slot number is equal or less than parent block")

    val epochNumberForVerifiedBlock = timeConverter.timeStampToEpochNumber(verifiedBlockTimestamp)
    val epochNumberForParentBlock = timeConverter.timeStampToEpochNumber(parentBlockTimestamp)
    if(epochNumberForVerifiedBlock - epochNumberForParentBlock > 1) throw new IllegalStateException("Whole epoch had been skipped") //any additional actions here?
  }

  private def verifyTimestampInFuture(verifiedBlockTimestamp: Block.Timestamp, history: SidechainHistory): Unit = {
    // According to Ouroboros Praos paper (page 5: "Time and Slots"): Block timestamp is valid,
    // if it belongs to the same or earlier Slot than current time Slot.
    // Check if timestamp is not too far in the future
    if(history.timeStampToAbsoluteSlotNumber(verifiedBlockTimestamp) > history.timeStampToAbsoluteSlotNumber(Instant.now.getEpochSecond))
      throw new SidechainBlockSlotInFutureException("Block had been generated in the future")
  }

  /*
      Visual schema for possible cases:
      You can assume on different length of Consensus Epoch. For example, 7 or 9.
      Block absolute slots: 3 - 12
                                 |
      Ommers slots:    [5    ,   7   ,   11]
                        |        |        |
      Subommers slots: [4]      [6]   [9  ,  10]
                                       |
      Subommers slots:                [8]
      Ommer Container can include both the same consensus epoch ommers and/or previous consensus epoch ommers.
      Inclusion, of ommers from 2 consensus epochs (or more) before is not valid, because it means,
      that there is an empty epoch between Container and its parent, which is invalid case.
      Most specific case when at some Ommers-level, there are both ommers from previous and current epoch.
      In this case previous epoch ommers can lead to the current epoch Nonce different to the one known by our history.
      It should be taken in consideration during Ommers VRF calculation,
      so proper Nonce info should be used for such an Ommer with all sub-ommers recursively.
   */
  private[horizen] def verifyOmmers(ommersContainer: OmmersContainer,
                                    currentFullConsensusEpochInfo: FullConsensusEpochInfo,
                                    previousFullConsensusEpochInfoOpt: Option[FullConsensusEpochInfo],
                                    bestKnownParentId: ModifierId,
                                    bestKnownParentInfo: SidechainBlockInfo,
                                    history: SidechainHistory,
                                    previousEpochOmmersInfoAccumulator: Seq[(VrfOutput, ConsensusSlotNumber)]
                                   ): Unit = {
    val ommers = ommersContainer.ommers
    if(ommers.isEmpty)
      return

    val ommersContainerEpochNumber: ConsensusEpochNumber = history.timeStampToEpochNumber(ommersContainer.header.timestamp)

    var accumulator: Seq[(VrfOutput, ConsensusSlotNumber)] = previousEpochOmmersInfoAccumulator
    var previousOmmerEpochNumber: ConsensusEpochNumber = ommersContainerEpochNumber
    var ommerCurrentFullConsensusEpochInfo = currentFullConsensusEpochInfo
    var ommerPreviousFullConsensusEpochInfoOpt = previousFullConsensusEpochInfoOpt

    for(ommer <- ommers) {
      val ommerEpochAndSlot: ConsensusEpochAndSlot = history.timestampToEpochAndSlot(ommer.header.timestamp)

      if(ommerEpochAndSlot.epochNumber < previousOmmerEpochNumber) {
        // First ommer is from previous consensus epoch to Ommer Container epoch.
        ommerCurrentFullConsensusEpochInfo = previousFullConsensusEpochInfoOpt
          .getOrElse(throw new IllegalStateException(s"Block ${ommersContainer.header.id} contains ommer two epochs before."))
        // We are not allow to have an ommers 2 epoch before ommer container.
        // It means that between block and its parent the whole epoch was skipped.
        ommerPreviousFullConsensusEpochInfoOpt = None
      } else if(ommerEpochAndSlot.epochNumber > previousOmmerEpochNumber) {
        // Ommer switched the consensus epoch (previous ommer was from previous epoch).
        // It means, that bestKnownParentId (parent of verified block) is also from previous epoch.
        // So calculate the nonce again with passing info of all Ommers from previous epoch as well.
        val nonce = history.calculateNonceForNonGenesisEpoch(bestKnownParentId, bestKnownParentInfo, accumulator)
        ommerCurrentFullConsensusEpochInfo = FullConsensusEpochInfo(currentFullConsensusEpochInfo.stakeConsensusEpochInfo, nonce)
        ommerPreviousFullConsensusEpochInfoOpt = previousFullConsensusEpochInfoOpt
      }

      val ommerVrfOutput: VrfOutput = history.getVrfOutput(ommer.header, ommerCurrentFullConsensusEpochInfo.nonceConsensusEpochInfo)
        .getOrElse(throw new IllegalStateException(s"VRF check for Ommer ${ommer.header.id} had been failed"))
      verifyForgingStakeInfo(ommer.header, ommerCurrentFullConsensusEpochInfo.stakeConsensusEpochInfo, ommerVrfOutput)

      verifyOmmers(ommer, ommerCurrentFullConsensusEpochInfo, ommerPreviousFullConsensusEpochInfoOpt,
        bestKnownParentId, bestKnownParentInfo, history, accumulator)

      // Add previous epoch ommer info to accumulated sequence.
      if(ommerEpochAndSlot.epochNumber < ommersContainerEpochNumber) {
        // prepend accumulator with ommer with more recent slot
        accumulator = (ommerVrfOutput, ommerEpochAndSlot.slotNumber) +: accumulator
      }
      previousOmmerEpochNumber = ommerEpochAndSlot.epochNumber
    }
  }

  //Verify that forging stake info in block is correct (including stake), exist in history and had enough stake to be forger
  private[horizen] def verifyForgingStakeInfo(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfOutput: VrfOutput): Unit = {
    log.debug(s"Verify Forger box against root hash: ${stakeConsensusEpochInfo.rootHash} by merkle path ${header.forgingStakeMerklePath.bytes().deep.mkString}")

    val forgingStakeIsCorrect = stakeConsensusEpochInfo.rootHash.sameElements(header.forgingStakeMerklePath.apply(header.forgingStakeInfo.hash))
    if (!forgingStakeIsCorrect) {
      log.debug(s"Actual stakeInfo: rootHash: ${stakeConsensusEpochInfo.rootHash}, totalStake: ${stakeConsensusEpochInfo.totalStake}")
      throw new IllegalStateException(s"Forging stake merkle path in block ${header.id} is inconsistent to stakes merkle root hash ${stakeConsensusEpochInfo.rootHash.deep.mkString(",")}")
    }

    val value = header.forgingStakeInfo.stakeAmount

    val stakeIsEnough = vrfProofCheckAgainstStake(vrfOutput, value, stakeConsensusEpochInfo.totalStake)
    if (!stakeIsEnough) {
      throw new IllegalArgumentException(
        s"Stake value in forger box in block ${header.id} is not enough for to be forger.")
    }
  }
}

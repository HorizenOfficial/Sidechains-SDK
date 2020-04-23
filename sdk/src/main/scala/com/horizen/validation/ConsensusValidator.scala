package com.horizen.validation
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{OmmersContainer, SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.consensus._
import com.horizen.proof.VrfProof
import com.horizen.vrf.VrfProofHash
import scorex.core.block.Block
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.mutable.ListBuffer
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

    val vrfSignIsNotCorrect = false //@TODO we should call verifyVfr and verifyForgerBox with consensusEpochInfo calculated from SC creation TX and a constant for nonce
    if (vrfSignIsNotCorrect) {
      throw new IllegalArgumentException(s"Genesis block timestamp is not signed his own forger box")
    }
  }

  private def validateNonGenesisBlock(verifiedBlock: SidechainBlock, history: SidechainHistory): Unit = {
    val parentBlockInfo: SidechainBlockInfo = history.storage.blockInfoById(verifiedBlock.parentId)
    verifyTimestamp(verifiedBlock.timestamp, parentBlockInfo.timestamp, history)

    val verifiedBlockInfo = history.blockToBlockInfo(verifiedBlock)
      .getOrElse(throw new IllegalArgumentException(s"Parent is missing for block ${verifiedBlock.id}")) //currently it is only reason if blockInfo is not calculated
    val fullConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(verifiedBlock.id, verifiedBlockInfo)

    val slotNumber = history.timeStampToSlotNumber(verifiedBlock.timestamp)
    val vrfMessage = buildVrfMessage(slotNumber, fullConsensusEpochInfo.nonceConsensusEpochInfo)

    verifyVrfProofAndHash(history, verifiedBlock.header, vrfMessage)
    verifyForgerBox(verifiedBlock.header, fullConsensusEpochInfo.stakeConsensusEpochInfo)

    val previousEpochLastBlockId = lastBlockIdInEpochId(history.getPreviousConsensusEpochIdForBlock(verifiedBlock.id, verifiedBlockInfo))
    val previousFullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(previousEpochLastBlockId, history.blockInfoById(previousEpochLastBlockId))
    verifyOmmers(verifiedBlock, fullConsensusEpochInfo, previousFullConsensusEpochInfo, verifiedBlock.parentId, parentBlockInfo, history)
  }

  private def verifyTimestamp(verifiedBlockTimestamp: Block.Timestamp, parentBlockTimestamp: Block.Timestamp, history: SidechainHistory): Unit = {
    // TODO: discuss it, cause problems with STF
    // NOTE: We already check this in less strict way in SidechainBlockHeader.semanticValidity()
    //if (verifiedBlockTimestamp > Instant.now.getEpochSecond) throw new IllegalArgumentException("Block had been generated in the future")
    if (verifiedBlockTimestamp < parentBlockTimestamp) throw new IllegalArgumentException("Block had been generated before parent block had been generated")

    val absoluteSlotNumberForVerifiedBlock = history.timeStampToAbsoluteSlotNumber(verifiedBlockTimestamp)
    val absoluteSlotNumberForParentBlock = history.timeStampToAbsoluteSlotNumber(parentBlockTimestamp)
    if (absoluteSlotNumberForVerifiedBlock <= absoluteSlotNumberForParentBlock) throw new IllegalArgumentException("Block absolute slot number is equal or less than parent block")

    val epochNumberForVerifiedBlock = history.timeStampToEpochNumber(verifiedBlockTimestamp)
    val epochNumberForParentBlock = history.timeStampToEpochNumber(parentBlockTimestamp)
    if(epochNumberForVerifiedBlock - epochNumberForParentBlock> 1) throw new IllegalStateException("Whole epoch had been skipped") //any additional actions here?
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
                                    previousFullConsensusEpochInfo: FullConsensusEpochInfo,
                                    bestKnownParentId: ModifierId,
                                    bestKnownParentInfo: SidechainBlockInfo,
                                    history: SidechainHistory,
                                    previousEpochOmmersInfoAccumulator: Seq[(VrfProof, VrfProofHash, ConsensusSlotNumber)] = Seq()
                                   ): Unit = {
    val ommers = ommersContainer.ommers
    if(ommers.isEmpty)
      return

    val ommersContainerEpochNumber: ConsensusEpochNumber = history.timeStampToEpochNumber(ommersContainer.header.timestamp)

    var accumulator: Seq[(VrfProof, VrfProofHash, ConsensusSlotNumber)] = previousEpochOmmersInfoAccumulator
    var previousOmmerEpochNumber: ConsensusEpochNumber = ommersContainerEpochNumber
    var ommerFullConsensusEpochInfo = currentFullConsensusEpochInfo

    for(ommer <- ommers) {
      val ommerEpochAndSlot: ConsensusEpochAndSlot = history.timestampToEpochAndSlot(ommer.header.timestamp)

      if(ommerEpochAndSlot.epochNumber < previousOmmerEpochNumber) {
        // First ommer is from previous consensus epoch to Ommer Container epoch.
        ommerFullConsensusEpochInfo = previousFullConsensusEpochInfo
      } else if(ommerEpochAndSlot.epochNumber > previousOmmerEpochNumber) {
        // Ommer switched the consensus epoch (previous ommer was from previous epoch).
        // It means, that bestKnownParentId (parent of verified block) is also from previous epoch.
        // So calculate the nonce again with passing info of all Ommers from previous epoch as well.
        val nonce = history.calculateNonceForNonGenesisEpoch(bestKnownParentId, bestKnownParentInfo, accumulator.to[ListBuffer])
        ommerFullConsensusEpochInfo = FullConsensusEpochInfo(currentFullConsensusEpochInfo.stakeConsensusEpochInfo, nonce)
      }

      val vrfMessage = buildVrfMessage(ommerEpochAndSlot.slotNumber, ommerFullConsensusEpochInfo.nonceConsensusEpochInfo)
      verifyVrfProofAndHash(history, ommer.header, vrfMessage)
      verifyForgerBox(ommer.header, ommerFullConsensusEpochInfo.stakeConsensusEpochInfo)

      verifyOmmers(ommer, ommerFullConsensusEpochInfo, previousFullConsensusEpochInfo,
        bestKnownParentId, bestKnownParentInfo, history, accumulator)

      // Add previous epoch ommer info to accumulated sequence.
      if(ommerEpochAndSlot.epochNumber < ommersContainerEpochNumber)
        accumulator = accumulator :+ (ommer.header.vrfProof, ommer.header.vrfProofHash, ommerEpochAndSlot.slotNumber)
      previousOmmerEpochNumber = ommerEpochAndSlot.epochNumber
    }
  }

  private[horizen] def verifyVrfProofAndHash(history: SidechainHistory, header: SidechainBlockHeader, message: VrfMessage): Unit = {
    val vrfIsCorrect = header.forgerBox.vrfPubKey().verify(message, header.vrfProof)
    if(!vrfIsCorrect) {
      throw new IllegalStateException(s"VRF check for block ${header.id} had been failed")
    }

    val calculatedVrfProofHash: VrfProofHash = header.vrfProof.proofToVRFHash(header.forgerBox.vrfPubKey(), message)
    if (!calculatedVrfProofHash.equals(header.vrfProofHash)) {
      throw new IllegalStateException(s"Vrf proof hash is corrupted for block ${header.id}")
    }
  }

  //Verify that forger box in block is correct (including stake), exist in history and had enough stake to be forger
  private[horizen] def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo): Unit = {
    log.debug(s"Verify Forger box against root hash: ${stakeConsensusEpochInfo.rootHash} by merkle path ${header.forgerBoxMerklePath.bytes().deep.mkString}")

    val forgerBoxIsCorrect = stakeConsensusEpochInfo.rootHash.sameElements(header.forgerBoxMerklePath.apply(header.forgerBox.id()))
    if (!forgerBoxIsCorrect) {
      log.debug(s"Actual stakeInfo: rootHash: ${stakeConsensusEpochInfo.rootHash}, totalStake: ${stakeConsensusEpochInfo.totalStake}")
      throw new IllegalStateException(s"Forger box merkle path in block ${header.id} is inconsistent to stakes merkle root hash ${stakeConsensusEpochInfo.rootHash.deep.mkString(",")}")
    }

    val value = header.forgerBox.value()

    val stakeIsEnough = vrfProofCheckAgainstStake(header.vrfProofHash, value, stakeConsensusEpochInfo.totalStake)
    if (!stakeIsEnough) {
      throw new IllegalArgumentException(
        s"Stake value in forger box in block ${header.id} is not enough for to be forger.")
    }
  }
}

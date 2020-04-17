package com.horizen.validation
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{OmmersContainer, SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.consensus.{NonceConsensusEpochInfo, _}
import scorex.core.block.Block
import scorex.util.ScorexLogging

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
    val verifiedBlockInfo = history.blockToBlockInfo(verifiedBlock)
      .getOrElse(throw new IllegalArgumentException(s"Parent is missing for block ${verifiedBlock.id}")) //currently it is only reason if blockInfo is not calculated
    val fullConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(verifiedBlock.id, verifiedBlockInfo)

    verifyVrf(history, verifiedBlock.header, fullConsensusEpochInfo.nonceConsensusEpochInfo)
    verifyForgerBox(verifiedBlock.header, fullConsensusEpochInfo.stakeConsensusEpochInfo)

    val previousEpochLastBlockId = lastBlockIdInEpochId(history.getPreviousConsensusEpochIdForBlock(verifiedBlock.id, verifiedBlockInfo))
    val previousFullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(previousEpochLastBlockId, history.blockInfoById(previousEpochLastBlockId))
    verifyOmmers(verifiedBlock, fullConsensusEpochInfo, previousFullConsensusEpochInfo, history)

    val parentBlockInfo: SidechainBlockInfo = history.storage.blockInfoById(verifiedBlock.parentId)
    verifyTimestamp(verifiedBlock.timestamp, parentBlockInfo.timestamp, history)
  }

  private def verifyTimestamp(verifiedBlockTimestamp: Block.Timestamp, parentBlockTimestamp: Block.Timestamp, history: SidechainHistory): Unit = {
    if (verifiedBlockTimestamp < parentBlockTimestamp) throw new IllegalArgumentException("Block had been generated before parent block had been generated")

    val absoluteSlotNumberForVerifiedBlock = history.timeStampToAbsoluteSlotNumber(verifiedBlockTimestamp)
    val absoluteSlotNumberForParentBlock = history.timeStampToAbsoluteSlotNumber(parentBlockTimestamp)
    if (absoluteSlotNumberForVerifiedBlock <= absoluteSlotNumberForParentBlock) throw new IllegalArgumentException("Block absolute slot number is equal or less than parent block")

    val epochNumberForVerifiedBlock = history.timeStampToEpochNumber(verifiedBlockTimestamp)
    val epochNumberForParentBlock = history.timeStampToEpochNumber(parentBlockTimestamp)
    if(epochNumberForVerifiedBlock - epochNumberForParentBlock> 1) throw new IllegalStateException("Whole epoch had been skipped") //any additional actions here?

    // According to Ouroboros Praos paper (page 5: "Time and Slots"): Block timestamp is valid,
    // if it belongs to the same or earlier Slot than current time Slot.
    // Check if timestamp is not too far in the future
    if(history.timeStampToAbsoluteSlotNumber(verifiedBlockTimestamp) > history.timeStampToAbsoluteSlotNumber(Instant.now.getEpochSecond))
      throw new SidechainBlockSlotInFutureException("Block had been generated in the future")
  }

  private[horizen] def verifyOmmers(ommersContainer: OmmersContainer,
                                     currentFullConsensusEpochInfo: FullConsensusEpochInfo,
                                     previousFullConsensusEpochInfo: FullConsensusEpochInfo,
                                     history: SidechainHistory): Unit = {
    val ommers = ommersContainer.ommers
    if(ommers.isEmpty)
      return

    val ommersContainerEpochNumber: ConsensusEpochNumber = history.timeStampToEpochNumber(ommersContainer.header.timestamp)

    // Ommers can be from the same ConsensusEpoch as OmmersContainer or from previous epoch.
    // If first part of Ommers is from previous epoch, they can have data, that will lead to different FullConsensusEpochInfo for current epoch.
    // So the second part of Ommers should be verified against different FullConsensusEpochInfo: nonce part can be different
    // With current Nonce calculation algorithm, it's not possible to retrieve MainchainHeaders with RefData, so no way to recalculate proper Nonce.
    // Solutions: Nonce must be taken not from the whole MCRefs most PoW header, but like in original Ouroboros from the VRF, that is a part of SidechainBlockHeader.
    var isPreviousEpochOmmer: Boolean = false
    for(ommer <- ommers) {
      val ommerEpochNumber: ConsensusEpochNumber = history.timeStampToEpochNumber(ommer.header.timestamp)
      // Fork occurs in previous consensus epoch
      if(ommerEpochNumber < ommersContainerEpochNumber) {
        isPreviousEpochOmmer = true
        verifyVrf(history, ommer.header, previousFullConsensusEpochInfo.nonceConsensusEpochInfo)
        verifyForgerBox(ommer.header, previousFullConsensusEpochInfo.stakeConsensusEpochInfo)

        verifyOmmers(ommer, previousFullConsensusEpochInfo, null, history)
      }
      else { // Equals
        if(isPreviousEpochOmmer) {
          // We Have Ommers form different epochs
          throw new IllegalStateException("Ommers from both previous and current ConsensusEpoch are not supported.")
        }
        verifyVrf(history, ommer.header, currentFullConsensusEpochInfo.nonceConsensusEpochInfo)
        verifyForgerBox(ommer.header, currentFullConsensusEpochInfo.stakeConsensusEpochInfo)

        verifyOmmers(ommer, currentFullConsensusEpochInfo, previousFullConsensusEpochInfo, history)
      }
    }

  }

  private[horizen] def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, nonceInfo: NonceConsensusEpochInfo): Unit = {
    val message = buildVrfMessage(history.timeStampToSlotNumber(header.timestamp), nonceInfo)

    val vrfIsCorrect = header.forgerBox.vrfPubKey().verify(message, header.vrfProof)
    if(!vrfIsCorrect) {
      throw new IllegalStateException(s"VRF check for block ${header.id} had been failed")
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

    val stakeIsEnough = vrfProofCheckAgainstStake(header.forgerBox.value(), header.vrfProof, stakeConsensusEpochInfo.totalStake)
    if (!stakeIsEnough) {
      throw new IllegalArgumentException(
        s"Stake value in forger box in block ${header.id} is not enough for to be forger.")
    }
  }
}

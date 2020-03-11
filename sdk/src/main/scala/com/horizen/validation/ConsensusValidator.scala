package com.horizen.validation
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
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
    val parentBlockInfo: SidechainBlockInfo = history.storage.blockInfoById(verifiedBlock.parentId)
    verifyTimestamp(verifiedBlock.timestamp, parentBlockInfo.timestamp, history)

    val blockInfo = history.blockToBlockInfo(verifiedBlock)
      .getOrElse(throw new IllegalArgumentException(s"Parent is missing for block ${verifiedBlock.id}")) //currently it is only reason if blockInfo is not calculated
    val fullConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(verifiedBlock.id, blockInfo)

    verifyVrf(history, verifiedBlock.header, fullConsensusEpochInfo.nonceConsensusEpochInfo)
    verifyForgerBox(verifiedBlock.header, fullConsensusEpochInfo.stakeConsensusEpochInfo)

    verifyOmmers(verifiedBlock, fullConsensusEpochInfo,  history)
  }

  private def verifyTimestamp(verifiedBlockTimestamp: Block.Timestamp, parentBlockTimestamp: Block.Timestamp, history: SidechainHistory): Unit = {
    if (verifiedBlockTimestamp > Instant.now.getEpochSecond) throw new IllegalArgumentException("Block had been generated in the future")
    if (verifiedBlockTimestamp < parentBlockTimestamp) throw new IllegalArgumentException("Block had been generated before parent block had been generated")

    val absoluteSlotNumberForVerifiedBlock = history.timeStampToAbsoluteSlotNumber(verifiedBlockTimestamp)
    val absoluteSlotNumberForParentBlock = history.timeStampToAbsoluteSlotNumber(parentBlockTimestamp)
    if (absoluteSlotNumberForVerifiedBlock <= absoluteSlotNumberForParentBlock) throw new IllegalArgumentException("Block absolute slot number is equal or less than parent block")

    val epochNumberForVerifiedBlock = history.timeStampToEpochNumber(verifiedBlockTimestamp)
    val epochNumberForParentBlock = history.timeStampToEpochNumber(parentBlockTimestamp)
    if(epochNumberForVerifiedBlock - epochNumberForParentBlock> 1) throw new IllegalStateException("Whole epoch had been skipped") //any additional actions here?
  }

  private def verifyOmmers(verifiedBlock: SidechainBlock, fullConsensusEpochInfo: FullConsensusEpochInfo, history: SidechainHistory): Unit = {
    val verifiedBlockEpochNumber = history.timeStampToEpochNumber(verifiedBlock.timestamp)
    val verifiedBlockSlotNumber = history.timeStampToSlotNumber(verifiedBlock.timestamp)

    // Last ommer epoch&slot number must be before current block one
    verifiedBlock.ommers.lastOption match {
      case Some(ommer) =>
        // TODO: put to separate utils function, that can compare timestamp in a context of slot/epoch
        val ommerEpochNumber = history.timeStampToEpochNumber(ommer.sidechainBlockHeader.timestamp)
        val ommerSlotNumber = history.timeStampToSlotNumber(ommer.sidechainBlockHeader.timestamp)
        if(ommerEpochNumber > verifiedBlockEpochNumber ||
            (ommerEpochNumber == verifiedBlockEpochNumber && ommerSlotNumber >= verifiedBlockSlotNumber))
          throw new IllegalArgumentException("Block refers to the ommer that belongs at the same slot or after it.")
      case _ =>
    }

    for(ommer <- verifiedBlock.ommers) {
      val ommerEpochNumber = history.timeStampToEpochNumber(ommer.sidechainBlockHeader.timestamp)
      if(ommerEpochNumber == verifiedBlockEpochNumber) {
        // TODO: error messages from functions below is not enough. We should tell that it's for Ommer
        verifyVrf(history, ommer.sidechainBlockHeader, fullConsensusEpochInfo.nonceConsensusEpochInfo)
        verifyForgerBox(ommer.sidechainBlockHeader, fullConsensusEpochInfo.stakeConsensusEpochInfo)
      }
      else { // ommer is from epoch number before verifiedBlock epoch.
        // TODO: is it possible, if ommer is not from previous epoch but even earlier?
        // TODO get fullConsensusEpochInfo for ommer epoch
      }
    }
  }

  private def verifyVrf(history: SidechainHistory, header: SidechainBlockHeader, nonceInfo: NonceConsensusEpochInfo): Unit = {
    val message = buildVrfMessage(history.timeStampToSlotNumber(header.timestamp), nonceInfo)

    val vrfIsCorrect = header.forgerBox.vrfPubKey().verify(message, header.vrfProof)
    if(!vrfIsCorrect) {
      throw new IllegalStateException(s"VRF check for block ${header.id} had been failed")
    }
  }

  //Verify that forger box in block is correct (including stake), exist in history and had enough stake to be forger
  private def verifyForgerBox(header: SidechainBlockHeader, stakeConsensusEpochInfo: StakeConsensusEpochInfo): Unit = {
    log.debug(s"Verify Forger box against root hash: ${stakeConsensusEpochInfo.rootHash} by merkle path ${header.forgerBoxMerklePath.bytes().deep.mkString}")

    val forgerBoxIsCorrect = stakeConsensusEpochInfo.rootHash.sameElements(header.forgerBoxMerklePath.apply(header.forgerBox.id()))
    if (!forgerBoxIsCorrect) {
      log.debug(s"Actual stakeInfo: rootHash: ${stakeConsensusEpochInfo.rootHash}, totalStake: ${stakeConsensusEpochInfo.totalStake}")
      throw new IllegalStateException(s"Forger box merkle path in block ${header.id} is inconsistent to stakes merkle root hash ${stakeConsensusEpochInfo.rootHash}")
    }

    val stakeIsEnough = vrfProofCheckAgainstStake(header.forgerBox.value(), header.vrfProof, stakeConsensusEpochInfo.totalStake)
    if (!stakeIsEnough) {
      throw new IllegalArgumentException(
        s"Stake value in forger box in block ${header.id} is not enough for to be forger.")
    }
  }
}

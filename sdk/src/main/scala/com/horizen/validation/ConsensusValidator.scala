package com.horizen.validation
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.SidechainBlock
import com.horizen.chain.SidechainBlockInfo
import com.horizen.consensus._
import com.horizen.vrf.VrfProofHash
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

    verifyVrfProof(history, verifiedBlock)

    val stakeConsensusEpochInfo = history.getStakeConsensusEpochInfo(verifiedBlock.timestamp, verifiedBlock.parentId)
      .getOrElse(throw new IllegalStateException(s"No stake consensus data for block ${verifiedBlock.id}"))

    val vrfProofHash: VrfProofHash = history.getVrfProofHashForBlock(verifiedBlock)
    verifyForgerBox(verifiedBlock, stakeConsensusEpochInfo, vrfProofHash)
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

  private def verifyVrfProof(history: SidechainHistory, verifiedBlock: SidechainBlock): Unit = {
    val slotNumber = history.timeStampToSlotNumber(verifiedBlock.timestamp)
    val vrfMessage = buildVrfMessage(slotNumber, history.getOrCalculateNonceConsensusEpochInfo(verifiedBlock.timestamp, verifiedBlock.parentId))

    val vrfIsCorrect = verifiedBlock.forgerBox.vrfPubKey().verify(vrfMessage, verifiedBlock.vrfProof)
    if(!vrfIsCorrect) {
      throw new IllegalStateException(s"VRF check for block ${verifiedBlock.id} had been failed")
    }
  }

  //Verify that forger box in block is correct (including stake), exist in history and had enough stake to be forger
  private def verifyForgerBox(block: SidechainBlock, stakeConsensusEpochInfo: StakeConsensusEpochInfo, vrfProofHash: VrfProofHash): Unit = {
    log.debug(s"Verify Forger box against root hash: ${stakeConsensusEpochInfo.rootHash} by merkle path ${block.merklePath.bytes().deep.mkString}")

    val forgerBoxIsCorrect = stakeConsensusEpochInfo.rootHash.sameElements(block.merklePath.apply(block.forgerBox.id()))
    if (!forgerBoxIsCorrect) {
      log.debug(s"Actual stakeInfo: rootHash: ${stakeConsensusEpochInfo.rootHash}, totalStake: ${stakeConsensusEpochInfo.totalStake}")
      throw new IllegalStateException(s"Forger box merkle path in block ${block.id} is inconsistent to stakes merkle root hash ${stakeConsensusEpochInfo.rootHash.deep.mkString(",")}")
    }

    val value = block.forgerBox.value()

    val stakeIsEnough = vrfProofCheckAgainstStake(vrfProofHash, value, stakeConsensusEpochInfo.totalStake)
    if (!stakeIsEnough) {
      throw new IllegalArgumentException(
        s"Stake value in forger box in block ${block.id} is not enough for to be forger.")
    }
  }
}

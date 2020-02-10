package com.horizen.validation
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.SidechainBlock
import com.horizen.consensus.{NonceConsensusEpochInfo, _}
import scorex.util.ScorexLogging

import scala.util.Try

class ConsensusValidator extends HistoryBlockValidator with ScorexLogging {
  override def validate(block: SidechainBlock, history: SidechainHistory): Try[Unit] = Try {
    if (history.isNotGenesisBlock(block.id)) {
      validateNonGenesisBlock(block, history.getBlockById(block.parentId).get, history)
    }
    // shall we somehow verify genesis block?
  }

  private def validateNonGenesisBlock(block: SidechainBlock, parentBlock: SidechainBlock, history: SidechainHistory): Unit = {
    verifyTimestamp(block, parentBlock)

    val epochDelta = history.timeStampToEpochNumber(block.timestamp) - history.timeStampToEpochNumber(parentBlock.timestamp)
    if (epochDelta > 1) throw new IllegalArgumentException("Whole epoch had been skipped") //any additional actions here?

    val parentBlockIsLastInEpoch: Boolean = (epochDelta == 1)
    val fullConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlockId(parentBlock.id, parentBlockIsLastInEpoch)

    verifyVrf(history, block, fullConsensusEpochInfo.nonceConsensusEpochInfo)
    verifyForgerBox(block, fullConsensusEpochInfo.stakeConsensusEpochInfo)
  }

  private def verifyTimestamp(block: SidechainBlock, parentBlock: SidechainBlock): Unit = {
    if (block.timestamp > Instant.now.getEpochSecond) throw new IllegalArgumentException("Block had been generated in the future")
    if (block.timestamp < parentBlock.timestamp) throw new IllegalArgumentException("Block had been generated before parent block had been generated")
  }

  private def verifyVrf(history: SidechainHistory, block: SidechainBlock, nonceInfo: NonceConsensusEpochInfo): Unit = {
    val message = buildVrfMessage(history.timeStampToSlotNumber(block.timestamp), nonceInfo)

    val vrfIsCorrect = block.forgerBox.vrfPubKey().verify(message, block.vrfProof)
    if(!vrfIsCorrect) {
      throw new IllegalStateException(s"VRF check for block ${block.id} had been failed")
    }
  }

  //Verify that forger box in block is correct (including stake), exist in history and had enough stake to be forger
  private def verifyForgerBox(block: SidechainBlock, stakeConsensusEpochInfo: StakeConsensusEpochInfo): Unit = {
    log.debug(s"Verify Forger box against root hash: ${stakeConsensusEpochInfo.rootHash} by merkle path ${block.merklePath.bytes().deep.mkString}")

    val forgerBoxIsCorrect = stakeConsensusEpochInfo.rootHash.data.sameElements(block.merklePath.apply(block.forgerBox.id()))
    if (!forgerBoxIsCorrect) {
      log.debug(s"actual stakeInfo: rootHash: ${stakeConsensusEpochInfo.rootHash}, totalStake: ${stakeConsensusEpochInfo.totalStake}")
      throw new IllegalStateException(s"Forger box merkle path in block ${block.id} is not correct")
    }

    val relativeStake = (block.forgerBox.value().toDouble / stakeConsensusEpochInfo.totalStake.toDouble)
    val requiredRelativeStake = hashToStakePercent(block.vrfProof.proofToVRFHash())

    val stakeIsEnough = relativeStake > requiredRelativeStake
    if (!stakeIsEnough) {
      throw new IllegalArgumentException(s"Stake value in forger box in block ${block.id} is not enough for to be forger. Required relative stake ${requiredRelativeStake}, but actual is ${relativeStake}")
    }
  }
}

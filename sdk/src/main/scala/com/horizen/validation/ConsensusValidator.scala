package com.horizen.validation
import java.math.BigInteger
import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{Ommer, SidechainBlock, SidechainBlockHeader}
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

    val verifiedBlockInfo = history.blockToBlockInfo(verifiedBlock)
      .getOrElse(throw new IllegalArgumentException(s"Parent is missing for block ${verifiedBlock.id}")) //currently it is only reason if blockInfo is not calculated
    val fullConsensusEpochInfo: FullConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(verifiedBlock.id, verifiedBlockInfo)

    verifyVrf(history, verifiedBlock.header, fullConsensusEpochInfo.nonceConsensusEpochInfo)
    verifyForgerBox(verifiedBlock.header, fullConsensusEpochInfo.stakeConsensusEpochInfo)

    verifyOmmers(verifiedBlock, verifiedBlockInfo, fullConsensusEpochInfo, history)
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

  private def verifyOmmers(verifiedBlock: SidechainBlock,
                           verifiedBlockInfo: SidechainBlockInfo,
                           verifierBlockFullConsensusEpochInfo: FullConsensusEpochInfo,
                           history: SidechainHistory): Unit = {

    val ommers: Seq[Ommer] = verifiedBlock.ommers
    if(ommers.nonEmpty) {
      // Verify that Ommers order is valid in context of SidechainBlocks epoch&slot order
      // Last ommer epoch&slot number must be before verified block epoch&slot
      val headers = ommers.map(_.sidechainBlockHeader) ++ Seq(verifiedBlock.header)
      for(i <- 1 until headers.size) {
        try {
          verifyTimestamp(headers(i).timestamp, headers(i - 1).timestamp, history)
        } catch {
          case _: Throwable => throw new IllegalArgumentException("Block refers to the ommers that have invalid slot order")
        }

      }

      // Verify Ommers SidehcainBlockHeader VRF and ForgerBox
      val verifiedBlockEpochNumber: ConsensusEpochNumber = history.timeStampToEpochNumber(verifiedBlock.timestamp)
      var tempConsensusEpochInfo: FullConsensusEpochInfo = verifierBlockFullConsensusEpochInfo
      var tempEpochNumber: ConsensusEpochNumber = verifiedBlockEpochNumber
      var ommersMinimumHeaderHashOpt: Option[BigInteger] = None

      for(ommer <- ommers) {
        val ommerEpochNumber: ConsensusEpochNumber = history.timeStampToEpochNumber(ommer.sidechainBlockHeader.timestamp)

        // Fork occurs in previous withdrawal epoch
        if(ommerEpochNumber < verifiedBlockEpochNumber) {
          // Get previous FullConsensusEpochInfo if epoch is switched
          if(ommerEpochNumber != tempEpochNumber) {
            val previousEpochLastBlockId = lastBlockIdInEpochId(history.getPreviousConsensusEpochIdForBlock(verifiedBlock.id, verifiedBlockInfo))
            tempConsensusEpochInfo = history.getFullConsensusEpochInfoForBlock(previousEpochLastBlockId, history.blockInfoById(previousEpochLastBlockId))
            tempEpochNumber = ommerEpochNumber
          }
          // For previous epoch keep track on the Ommers' best PoW Header (lowest hash)
          getMinimalHashOpt(ommer.mainchainReferencesHeaders.map(_.hash)) match {
            case Some(minimumHeaderHash) =>
              ommersMinimumHeaderHashOpt = ommersMinimumHeaderHashOpt.map(_.min(minimumHeaderHash))
            case _ =>
          }

          verifyVrf(history, ommer.sidechainBlockHeader, tempConsensusEpochInfo.nonceConsensusEpochInfo)
          verifyForgerBox(ommer.sidechainBlockHeader, tempConsensusEpochInfo.stakeConsensusEpochInfo)
        }
        else { // Equals
          // Calculate proper FullConsensusEpochInfo if epoch is switched back to verifiedBlock epoch
          if(ommerEpochNumber != tempEpochNumber) {
            // It means that previous ommer was from previous consensus epoch.
            // So nonce for current epoch can be different if best PoW MCRef header occurred in previous ommers.
            val verifiedBlockConsensusNonce = verifierBlockFullConsensusEpochInfo.nonceConsensusEpochInfo.consensusNonce
            val actualConsensusNonce: ConsensusNonce = bigIntToConsensusNonce(
              ommersMinimumHeaderHashOpt.get.min(sha256HashToPositiveBigInteger(verifiedBlockConsensusNonce))
            )

            tempConsensusEpochInfo = FullConsensusEpochInfo(
              verifierBlockFullConsensusEpochInfo.stakeConsensusEpochInfo,
              NonceConsensusEpochInfo(actualConsensusNonce)
            )
            tempEpochNumber = ommerEpochNumber
          }

          verifyVrf(history, ommer.sidechainBlockHeader, tempConsensusEpochInfo.nonceConsensusEpochInfo)
          verifyForgerBox(ommer.sidechainBlockHeader, tempConsensusEpochInfo.stakeConsensusEpochInfo)
        }
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

package com.horizen.forge

import com.horizen.block._
import com.horizen.box.NoncedBox
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proposition.Proposition
import com.horizen.transaction.SidechainTransaction
import com.horizen.vrf.VRFProof
import com.horizen.{ForgerDataWithSecrets, _}
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.util.{ModifierId, ScorexLogging, bytesToId}
import com.horizen.chain._

import scala.util.{Failure, Success, Try}


class ForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                          companion: SidechainTransactionsCompanion,
                          val params: NetworkParams) extends ScorexLogging with TimeToEpochSlotConverter {
  type ForgeMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult]

  case class BranchPointInfo(branchPointId: ModifierId, referenceDataToInclude: Seq[MainchainHeaderHash], headersToInclude: Seq[MainchainHeaderHash])

  def buildForgeMessageForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber): ForgeMessageType = {
      val forgingFunctionForEpochAndSlot: View => ForgeResult = tryToForgeNextBlock(consensusEpochNumber, consensusSlotNumber)

      val forgeMessage: ForgeMessageType =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult](forgingFunctionForEpochAndSlot)

      forgeMessage
  }

  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber)(view: View): ForgeResult = {
    log.info(s"Try to forge block for epoch ${nextConsensusEpochNumber} with slot ${nextConsensusSlotNumber}")
    val branchPointInfo = getBranchPointInfo(view.history) match {
      case Success(info) => info
      case Failure(ex) => return ForgeFailed(ex)
    }

    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    val parentBlockInfo = view.history.blockInfoById(parentBlockId)
    val parentBlockEpochAndSlot = timestampToEpochAndSlot(parentBlockInfo.timestamp)

    val nextBlockTimestamp = getTimeStampForEpochAndSlot(nextConsensusEpochNumber, nextConsensusSlotNumber)
    val nextBlockEpochAndSlot: ConsensusEpochAndSlot = ConsensusEpochAndSlot(nextConsensusEpochNumber, nextConsensusSlotNumber)
    if(parentBlockEpochAndSlot >= nextBlockEpochAndSlot) {
      ForgeFailed(new IllegalArgumentException (s"Try to forge block with epochAndSlot ${nextBlockEpochAndSlot} but current best block epochAndSlot are: ${parentBlockEpochAndSlot}"))
    }

    if ((nextConsensusEpochNumber - timeStampToEpochNumber(parentBlockInfo.timestamp)) > 1) log.warn("Forging is not possible: whole consensus epoch(s) are missed")

    val consensusInfo: FullConsensusEpochInfo = view.history.getFullConsensusEpochInfoForNextBlock(parentBlockId, nextConsensusEpochNumber)
    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)
    val availableForgersDataWithSecret: Seq[ForgerDataWithSecrets] = view.vault.getForgingDataWithSecrets(nextConsensusEpochNumber).getOrElse(Seq())

    val forgingDataOpt: Option[(ForgerDataWithSecrets, VRFProof)] = availableForgersDataWithSecret
      .toStream
      .map(forgerDataWithSecrets => (forgerDataWithSecrets, forgerDataWithSecrets.vrfSecret.prove(vrfMessage))) //get secrets thus filter forger boxes not owned by node
      .find{case (forgerDataWithSecrets, vrfProof) => vrfProofCheckAgainstStake(forgerDataWithSecrets.forgerBox.value(), vrfProof, totalStake)} //check our forger boxes against stake

    val forgingResult = forgingDataOpt
                                      .map{case (forgerDataWithSecrets, vrfProof) => forgeBlock(view, nextBlockTimestamp, branchPointInfo, forgerDataWithSecrets, vrfProof)}
                                      .getOrElse(SkipSlot)

    log.info(s"Forge result is: ${forgingResult}")
    forgingResult
  }

  protected def getBranchPointInfo(history: SidechainHistory): Try[BranchPointInfo] = Try {
    val bestMainchainHeaderInfo = history.getBestMainchainHeaderInfo.get

    val (firstKnownHashHeight: Int, headerHashes: Seq[MainchainHeaderHash]) =
      mainchainSynchronizer.getMainchainDivergentSuffix(history, MainchainSynchronizer.MAX_BLOCKS_REQUEST) match {
        case Success((height, hashes)) => (height, hashes)
        case Failure(ex) =>
          // For regtest Forger is allowed to produce next block in case if there is no MC Node connection
          if (params.isInstanceOf[RegTestParams])
            (bestMainchainHeaderInfo.height, Seq(bestMainchainHeaderInfo.hash))
          else
            throw ex
      }

    // Check that there is no orphaned mainchain headers: SC most recent mainchain header is a part of MC active chain
    if(firstKnownHashHeight == bestMainchainHeaderInfo.height) {
      val branchPointId = history.bestBlockId
      var withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength - history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex
      if (withdrawalEpochMcBlocksLeft == 0) // current best block is the last block of the epoch
        withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

      val maxReferenceDataNumber: Int = Math.min(SidechainBlock.MAX_MC_BLOCKS_NUMBER, withdrawalEpochMcBlocksLeft) // to not to include mcblock references from different withdrawal epochs

      val missedMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = history.missedMainchainReferenceDataHeaderHashes
      val nextMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = missedMainchainReferenceDataHeaderHashes ++ headerHashes.tail

      val mainchainReferenceDataHeaderHashesToInclude = nextMainchainReferenceDataHeaderHashes.take(maxReferenceDataNumber)
      val mainchainHeadersHashesToInclude = mainchainReferenceDataHeaderHashesToInclude.filter(hash => !missedMainchainReferenceDataHeaderHashes.contains(hash))

      BranchPointInfo(branchPointId, mainchainReferenceDataHeaderHashesToInclude, mainchainHeadersHashesToInclude)
    }
    else { // Some blocks in SC Active chain contains orphaned MainchainHeaders
      val orphanedMainchainHeadersNumber: Int = bestMainchainHeaderInfo.height - firstKnownHashHeight
      val newMainchainHeadersNumber = headerHashes.size - 1

      if (orphanedMainchainHeadersNumber >= newMainchainHeadersNumber) {
        ForgeFailed(new Exception("No sense to forge: active branch contains orphaned MainchainHeaders, that number is greater or equal to actual new MainchainHeaders."))
      }

      val commonMainchainHeaderInfo = history.getMainchainHeaderInfoByHeight(firstKnownHashHeight).get
      val commonSidechainBlockId: ModifierId = commonMainchainHeaderInfo.sidechainBlockId
      val commonSidechainBlockInfo: SidechainBlockInfo = history.blockInfoById(commonSidechainBlockId)

      if (commonSidechainBlockInfo.mainchainHeaderHashes.last.equals(commonMainchainHeaderInfo.hash)) {
        // Common MainchainHeader is the last header inside the containing SidechainBlock, so no orphaned MainchainHeaders present in SidechainBlock
        BranchPointInfo(commonSidechainBlockId, Seq(), headerHashes.tail)
      }
      else {
        //  SidechainBlock also contains some orphaned MainchainHeaders
        BranchPointInfo(commonSidechainBlockInfo.parentId, Seq(),
          commonSidechainBlockInfo.mainchainHeaderHashes.takeWhile(hash => !hash.equals(commonMainchainHeaderInfo.hash)) ++ headerHashes.tail)
      }
    }
  }

  protected def forgeBlock(view: View, timestamp: Long, branchPointInfo: BranchPointInfo, forgerDataWithSecrets: ForgerDataWithSecrets, vrfProof: VRFProof): ForgeResult = {
    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    val parentBlockInfo: SidechainBlockInfo = view.history.blockInfoById(branchPointInfo.branchPointId)
    var withdrawalEpochMcBlocksLeft: Int = params.withdrawalEpochLength - parentBlockInfo.withdrawalEpochInfo.lastEpochIndex
    if (withdrawalEpochMcBlocksLeft == 0) // parent block is the last block of the epoch
      withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

    // Get all needed MainchainBlockReferences from MC Node
    val allMainchainHashes: Seq[MainchainHeaderHash] = (branchPointInfo.referenceDataToInclude ++ branchPointInfo.headersToInclude).distinct
    val mainchainBlockReferences: Seq[MainchainBlockReference] =
      mainchainSynchronizer.getMainchainBlockReferences(view.history, allMainchainHashes) match {
        case Success(references) => references
        case Failure(ex) => return ForgeFailed(ex)
      }

    // Extract proper MainchainReferenceData
    val mainchainReferenceData: Seq[MainchainBlockReferenceData] =
      mainchainBlockReferences.filter(ref => branchPointInfo.referenceDataToInclude.contains(byteArrayToMainchainHeaderHash(ref.header.hash)))
      .map(_.data)

    // Extract proper MainchainHeaders
    val mainchainHeaders: Seq[MainchainHeader] =
      mainchainBlockReferences.filter(ref => branchPointInfo.headersToInclude.contains(byteArrayToMainchainHeaderHash(ref.header.hash)))
        .map(_.header)

    // Get transactions if possible
    val transactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] =
      if (branchPointInfo.referenceDataToInclude.size == withdrawalEpochMcBlocksLeft) { // SC block is going to become the last block of the withdrawal epoch
        Seq() // no SC Txs allowed
      } else { // SC block is in the middle of the epoch
        view.pool.take(SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER) // TO DO: problems with types
          .map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
          .toSeq
      }

    // Get ommers in case if branch point is not current best block
    var ommers: Seq[Ommer] = Seq()
    var blockId = view.history.bestBlockId
    while (blockId != branchPointInfo.branchPointId) {
      val block = view.history.getBlockById(blockId).get() // TODO: replace with method blockById
      blockId = block.parentId
      ommers = Ommer.toOmmer(block) +: ommers
    }

    val tryBlock = SidechainBlock.create(
      parentBlockId,
      timestamp,
      mainchainReferenceData,
      transactions,
      mainchainHeaders,
      ommers,
      forgerDataWithSecrets.forgerBoxRewardPrivateKey,
      forgerDataWithSecrets.forgerBox,
      vrfProof,
      forgerDataWithSecrets.merklePath,
      companion,
      params)

    tryBlock match {
      case Success(block) => ForgeSuccess(block)
      case Failure(exception) => ForgeFailed(exception)
    }
  }
}





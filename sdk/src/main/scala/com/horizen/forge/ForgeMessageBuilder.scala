package com.horizen.forge

import akka.util.Timeout
import com.horizen.block._
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, VrfSecretKey}
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.{ForgingStakeMerklePathInfo, ListSerializer, MerkleTree, TimeToEpochUtils}
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.JavaConverters._
import com.horizen.vrf.VrfOutput

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

class ForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                          companion: SidechainTransactionsCompanion,
                          val params: NetworkParams,
                          allowNoWebsocketConnectionInRegtest: Boolean) extends ScorexLogging {
  type ForgeMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult]

  case class BranchPointInfo(branchPointId: ModifierId, referenceDataToInclude: Seq[MainchainHeaderHash], headersToInclude: Seq[MainchainHeaderHash])

  def buildForgeMessageForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber, timeout: Timeout): ForgeMessageType = {
      val forgingFunctionForEpochAndSlot: View => ForgeResult = tryToForgeNextBlock(consensusEpochNumber, consensusSlotNumber, timeout)

      val forgeMessage: ForgeMessageType =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult](forgingFunctionForEpochAndSlot)

      forgeMessage
  }

  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber, timeout: Timeout)(nodeView: View): ForgeResult = Try {
    log.info(s"Try to forge block for epoch $nextConsensusEpochNumber with slot $nextConsensusSlotNumber")

    val branchPointInfo: BranchPointInfo = getBranchPointInfo(nodeView.history) match {
      case Success(info) => info
      case Failure(ex) => return ForgeFailed(ex)
    }

    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    val parentBlockInfo = nodeView.history.blockInfoById(parentBlockId)

    checkNextEpochAndSlot(parentBlockInfo.timestamp, nodeView.history.bestBlockInfo.timestamp, nextConsensusEpochNumber, nextConsensusSlotNumber)

    val nextBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, nextConsensusEpochNumber, nextConsensusSlotNumber)
    val consensusInfo: FullConsensusEpochInfo = nodeView.history.getFullConsensusEpochInfoForBlock(nextBlockTimestamp, parentBlockId)
    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)

    val sidechainWallet = nodeView.vault

    // Get ForgingStakeMerklePathInfo from wallet and order them by stake decreasing.
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] =
      sidechainWallet.getForgingStakeMerklePathInfoOpt(nextConsensusEpochNumber).getOrElse(Seq())
        .sortWith(_.forgingStakeInfo.stakeAmount > _.forgingStakeInfo.stakeAmount)

    if (forgingStakeMerklePathInfoSeq.isEmpty) {
      NoOwnedForgingStake
    } else {
      val ownedForgingDataView: Seq[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)]
      = forgingStakeMerklePathInfoSeq.view.flatMap(forgingStakeMerklePathInfo => getSecretsAndProof(sidechainWallet, vrfMessage, forgingStakeMerklePathInfo))

      val eligibleForgingDataView: Seq[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)]
      = ownedForgingDataView.filter { case (forgingStakeMerklePathInfo, _, _, vrfOutput) =>
        vrfProofCheckAgainstStake(vrfOutput, forgingStakeMerklePathInfo.forgingStakeInfo.stakeAmount, totalStake)
      }


      val eligibleForgerOpt = eligibleForgingDataView.headOption //force all forging related calculations

      val forgingResult = eligibleForgerOpt
        .map { case (forgingStakeMerklePathInfo, privateKey25519, vrfProof, _) =>
          forgeBlock(nodeView, nextBlockTimestamp, branchPointInfo, forgingStakeMerklePathInfo, privateKey25519, vrfProof, timeout)
        }
        .getOrElse(SkipSlot)
      forgingResult
    }
  }
    match {
      case Success(result) => {
        log.info(s"Forge result is: $result")
        result
      }
      case Failure(ex) => {
        log.error(s"Failed to forge block for ${nextConsensusEpochNumber} epoch ${nextConsensusSlotNumber} slot due:" , ex)
        ForgeFailed(ex)
    }
  }

  private def getSecretsAndProof(wallet: SidechainWallet,
                                 vrfMessage: VrfMessage,
                                 forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo): Option[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)] = {
    for {
      blockSignPrivateKey <- wallet.secret(forgingStakeMerklePathInfo.forgingStakeInfo.blockSignPublicKey).asInstanceOf[Option[PrivateKey25519]]
      vrfSecret <- wallet.secret(forgingStakeMerklePathInfo.forgingStakeInfo.vrfPublicKey).asInstanceOf[Option[VrfSecretKey]]
      vrfProofAndHash <- Some(vrfSecret.prove(vrfMessage))
    } yield {
      val vrfProof = vrfProofAndHash.getKey
      val vrfOutput = vrfProofAndHash.getValue
      (forgingStakeMerklePathInfo, blockSignPrivateKey, vrfProof, vrfOutput)
    }
  }

  private def checkNextEpochAndSlot(parentBlockTimestamp: Long,
                                    currentTipBlockTimestamp: Long,
                                    nextEpochNumber: ConsensusEpochNumber,
                                    nextSlotNumber: ConsensusSlotNumber): Unit = {
    // Parent block and current tip block can be the same in case of extension the Active chain.
    // But can be different in case of sidechain fork caused by mainchain fork.
    // In this case parent block is before the tip, and tip block will be the last Ommer included into the next block.
    val parentBlockEpochAndSlot: ConsensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, parentBlockTimestamp)
    val currentTipBlockEpochAndSlot: ConsensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, currentTipBlockTimestamp)
    val nextBlockEpochAndSlot: ConsensusEpochAndSlot = ConsensusEpochAndSlot(nextEpochNumber, nextSlotNumber)

    if(parentBlockEpochAndSlot >= nextBlockEpochAndSlot) {
      throw new IllegalArgumentException (s"Try to forge block with incorrect epochAndSlot $nextBlockEpochAndSlot which are equal or less than parent block epochAndSlot: $parentBlockEpochAndSlot")
    }

    if ((nextEpochNumber - parentBlockEpochAndSlot.epochNumber) > 1) {
      throw new IllegalArgumentException (s"Forging is not possible, because of whole consensus epoch is missed: current epoch = $nextEpochNumber, parent epoch = ${parentBlockEpochAndSlot.epochNumber}")
    }

    if(currentTipBlockEpochAndSlot >= nextBlockEpochAndSlot) {
      throw new IllegalArgumentException (s"Try to forge block with incorrect epochAndSlot $nextBlockEpochAndSlot which are equal or less than last ommer epochAndSlot: $currentTipBlockEpochAndSlot")
    }
  }

  private def getBranchPointInfo(history: SidechainHistory): Try[BranchPointInfo] = Try {
    val bestMainchainHeaderInfo = history.getBestMainchainHeaderInfo.get

    val (bestMainchainCommonPointHeight: Int, bestMainchainCommonPointHash: MainchainHeaderHash, newHeaderHashes: Seq[MainchainHeaderHash]) =
      mainchainSynchronizer.getMainchainDivergentSuffix(history, MainchainSynchronizer.MAX_BLOCKS_REQUEST) match {
        case Success((height, hashes)) => (height, hashes.head, hashes.tail) // hashes contains also the hash of best known block
        case Failure(ex) =>
          // For regtest Forger is allowed to produce next block in case if there is no MC Node connection
          if (params.isInstanceOf[RegTestParams] && allowNoWebsocketConnectionInRegtest)
            (bestMainchainHeaderInfo.height, bestMainchainHeaderInfo.hash, Seq())
          else
            throw ex
      }

    // Check that there is no orphaned mainchain headers: SC most recent mainchain header is a part of MC active chain
    if(bestMainchainCommonPointHash == bestMainchainHeaderInfo.hash) {
      val branchPointId: ModifierId = history.bestBlockId
      var withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength - history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex
      if (withdrawalEpochMcBlocksLeft == 0) // current best block is the last block of the epoch
        withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

      // to not to include mcblock references data from different withdrawal epochs
      val maxReferenceDataNumber: Int = withdrawalEpochMcBlocksLeft

      val missedMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = history.missedMainchainReferenceDataHeaderHashes
      val nextMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = missedMainchainReferenceDataHeaderHashes ++ newHeaderHashes

      val mainchainReferenceDataHeaderHashesToInclude = nextMainchainReferenceDataHeaderHashes.take(maxReferenceDataNumber)
      val mainchainHeadersHashesToInclude = newHeaderHashes

      BranchPointInfo(branchPointId, mainchainReferenceDataHeaderHashesToInclude, mainchainHeadersHashesToInclude)
    }
    else { // Some blocks in SC Active chain contains orphaned MainchainHeaders
      val orphanedMainchainHeadersNumber: Int = bestMainchainHeaderInfo.height - bestMainchainCommonPointHeight
      val newMainchainHeadersNumber = newHeaderHashes.size

      if (orphanedMainchainHeadersNumber >= newMainchainHeadersNumber) {
        ForgeFailed(new Exception("No sense to forge: active branch contains orphaned MainchainHeaders, that number is greater or equal to actual new MainchainHeaders."))
      }

      val firstOrphanedHashHeight: Int = bestMainchainCommonPointHeight + 1
      val firstOrphanedMainchainHeaderInfo = history.getMainchainHeaderInfoByHeight(firstOrphanedHashHeight).get
      val orphanedSidechainBlockId: ModifierId = firstOrphanedMainchainHeaderInfo.sidechainBlockId
      val orphanedSidechainBlockInfo: SidechainBlockInfo = history.blockInfoById(orphanedSidechainBlockId)

      if (firstOrphanedMainchainHeaderInfo.hash.equals(orphanedSidechainBlockInfo.mainchainHeaderHashes.head)) {
        // First orphaned MainchainHeader is the first header inside the containing SidechainBlock, so no common MainchainHeaders present in SidechainBlock before it
        BranchPointInfo(orphanedSidechainBlockInfo.parentId, Seq(), newHeaderHashes)
      }
      else {
        // SidechainBlock also contains some common MainchainHeaders before first orphaned MainchainHeader
        // So we should add that common MainchainHeaders to the SidechainBlock as well
        BranchPointInfo(orphanedSidechainBlockInfo.parentId, Seq(),
          orphanedSidechainBlockInfo.mainchainHeaderHashes.takeWhile(hash => !hash.equals(firstOrphanedMainchainHeaderInfo.hash)) ++ newHeaderHashes)
      }
    }
  }

  private def precalculateBlockHeaderSize(parentId: ModifierId,
                                          timestamp: Long,
                                          forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                                          vrfProof: VrfProof): Int = {
    // Create block header template, setting dummy values where it is possible.
    // Note: SidechainBlockHeader length is not constant because of the forgingStakeMerklePathInfo.merklePath.
    val header = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgingStakeMerklePathInfo.forgingStakeInfo,
      forgingStakeMerklePathInfo.merklePath,
      vrfProof,
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      Long.MaxValue,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    header.bytes.length
  }
  private def forgeBlock(nodeView: View,
                         timestamp: Long,
                         branchPointInfo: BranchPointInfo,
                         forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                         blockSignPrivateKey: PrivateKey25519,
                         vrfProof: VrfProof,
                         timeout: Timeout): ForgeResult = {
    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    val parentBlockInfo: SidechainBlockInfo = nodeView.history.blockInfoById(branchPointInfo.branchPointId)
    var withdrawalEpochMcBlocksLeft: Int = params.withdrawalEpochLength - parentBlockInfo.withdrawalEpochInfo.lastEpochIndex
    if (withdrawalEpochMcBlocksLeft == 0) // parent block is the last block of the epoch
      withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

    var blockSize: Int = precalculateBlockHeaderSize(branchPointInfo.branchPointId, timestamp, forgingStakeMerklePathInfo, vrfProof)

    // Get all needed MainchainBlockHeaders from MC Node
    val mainchainHeaderHashesToRetrieve: Seq[MainchainHeaderHash] = branchPointInfo.headersToInclude

    // Extract proper MainchainHeaders
    val mainchainHeaders: Seq[MainchainHeader] =
      mainchainSynchronizer.getMainchainBlockHeaders(mainchainHeaderHashesToRetrieve) match {
        case Success(headers) => headers
        case Failure(ex) => return ForgeFailed(ex)
      }

    // Update block size with MC Headers
    val mcHeadersSerializer = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)
    blockSize += mcHeadersSerializer.toBytes(mainchainHeaders.asJava).length

    // Get Ommers in case the branch point is not the current best block
    var ommers: Seq[Ommer] = Seq()
    var blockId = nodeView.history.bestBlockId
    while (blockId != branchPointInfo.branchPointId) {
      val block = nodeView.history.getBlockById(blockId).get() // TODO: replace with method blockById with no Option
      blockId = block.parentId
      ommers = Ommer.toOmmer(block) +: ommers
    }

    // Update block size with Ommers
    val ommersSerializer = new ListSerializer[Ommer](OmmerSerializer)
    blockSize += ommersSerializer.toBytes(ommers.asJava).length

    // Get all needed MainchainBlockReferences from the MC Node
    // Extract MainchainReferenceData and collect as much as the block can fit
    val mainchainBlockReferenceDataToRetrieve: Seq[MainchainHeaderHash] = branchPointInfo.referenceDataToInclude

    if(mainchainBlockReferenceDataToRetrieve.nonEmpty)
      blockSize += 4 // placeholder for the MainchainReferenceData Seq size value

    val mainchainReferenceData: ArrayBuffer[MainchainBlockReferenceData] = ArrayBuffer()
    // Collect MainchainRefData considering the actor message processing timeout
    // Note: We may do a lot of websocket `getMainchainBlockReference` operations that are a bit slow,
    // because they are processed one by one, so we limit requests in time.
    val startTime: Long = System.currentTimeMillis()
    mainchainBlockReferenceDataToRetrieve.takeWhile(hash => {
      mainchainSynchronizer.getMainchainBlockReference(hash) match {
        case Success(ref) => {
          val refDataSize = ref.data.bytes.length + 4 // placeholder for MainchainReferenceData length
          if(blockSize + refDataSize > SidechainBlock.MAX_BLOCK_SIZE)
            false // stop data collection
          else {
            mainchainReferenceData.append(ref.data)
            blockSize += refDataSize
            val isTimeout: Boolean = System.currentTimeMillis() - startTime >= timeout.duration.toMillis / 2
            !isTimeout // continue data collection
          }
        }
        case Failure(ex) => return ForgeFailed(ex)
      }
    })

    // Collect transactions if possible
    val transactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] =
      if (mainchainReferenceData.size == withdrawalEpochMcBlocksLeft) { // SC block is going to become the last block of the withdrawal epoch
        Seq() // no SC Txs allowed
      } else { // SC block is in the middle of the epoch
        blockSize += 4 // placeholder for the Transactions Seq size value
        var txsCounter: Int = 0
        nodeView.pool.take(nodeView.pool.size).filter(tx => {
          val txSize = tx.bytes.length + 4 // placeholder for Tx length
          txsCounter += 1
          if(txsCounter > SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER || blockSize + txSize > SidechainBlock.MAX_BLOCK_SIZE)
            false // stop data collection
          else {
            blockSize += txSize
            true // continue data collection
          }
        }).map(tx => tx.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]]).toSeq // TO DO: problems with types
      }

    val tryBlock = SidechainBlock.create(
      parentBlockId,
      timestamp,
      mainchainReferenceData,
      transactions,
      mainchainHeaders,
      ommers,
      blockSignPrivateKey,
      forgingStakeMerklePathInfo.forgingStakeInfo,
      vrfProof,
      forgingStakeMerklePathInfo.merklePath,
      companion)

    tryBlock match {
      case Success(block) => ForgeSuccess(block)
      case Failure(exception) => ForgeFailed(exception)
    }
  }
}





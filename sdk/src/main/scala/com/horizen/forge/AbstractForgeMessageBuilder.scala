package com.horizen.forge

import akka.util.Timeout
import com.horizen.block._
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.{PrivateKey25519, VrfSecretKey}
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.{Transaction, TransactionSerializer}
import com.horizen.utils.{DynamicTypedSerializer, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, TimeToEpochUtils}
import com.horizen.vrf.VrfOutput
import com.horizen.{AbstractHistory, AbstractWallet, consensus}
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.block.Block
import scorex.core.transaction.MemoryPool
import scorex.core.transaction.state.MinimalState
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

abstract class AbstractForgeMessageBuilder[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H]
  ] (
    mainchainSynchronizer: MainchainSynchronizer,
    companion: DynamicTypedSerializer[TX, TransactionSerializer[TX]],
    val params: NetworkParams,
    allowNoWebsocketConnectionInRegtest: Boolean) extends ScorexLogging
{
  type HSTOR <: AbstractHistoryStorage[PM, HSTOR]
  type VL <: AbstractWallet[TX, PM, VL]
  type HIS <: AbstractHistory[TX, H, PM, HSTOR, HIS]
  type MS <: MinimalState[PM, MS]
  type MP <: MemoryPool[TX, MP]

  type View = CurrentView[HIS, MS, VL, MP]

  type ForgeMessageType = GetDataFromCurrentView[ HIS,  MS,  VL,  MP, ForgeResult]

  def buildForgeMessageForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber, timeout: Timeout): ForgeMessageType = {
    val forgingFunctionForEpochAndSlot: View => ForgeResult = tryToForgeNextBlock(consensusEpochNumber, consensusSlotNumber, timeout)

    val forgeMessage: ForgeMessageType =
      GetDataFromCurrentView[ HIS,  MS,  VL,  MP, ForgeResult](forgingFunctionForEpochAndSlot)

    forgeMessage
  }

  case class BranchPointInfo(branchPointId: ModifierId, referenceDataToInclude: Seq[MainchainHeaderHash], headersToInclude: Seq[MainchainHeaderHash])

  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber, timeout: Timeout)(nodeView: View): ForgeResult = Try {
    log.info(s"Try to forge block for epoch $nextConsensusEpochNumber with slot $nextConsensusSlotNumber")

    val branchPointInfo: BranchPointInfo = getBranchPointInfo(nodeView.history) match {
      case Success(info) => info
      case Failure(ex) => return ForgeFailed(ex)
    }

    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    val parentBlockInfo = nodeView.history.blockInfoById(parentBlockId)

    checkNextEpochAndSlot(parentBlockInfo.timestamp, nodeView.history.bestBlockInfo.timestamp,
      nextConsensusEpochNumber, nextConsensusSlotNumber) match {
      case Some(forgeFailure) => return forgeFailure
      case _ => // checks passed
    }

    val nextBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, nextConsensusEpochNumber, nextConsensusSlotNumber)
    val consensusInfo: FullConsensusEpochInfo = nodeView.history.getFullConsensusEpochInfoForBlock(nextBlockTimestamp, parentBlockId)
    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)

    // Get ForgingStakeMerklePathInfo from wallet and order them by stake decreasing.
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] = getForgingStakeMerklePathInfo(nextConsensusEpochNumber, nodeView.vault)


    if (forgingStakeMerklePathInfoSeq.isEmpty) {
      NoOwnedForgingStake
    } else {
      val ownedForgingDataView: Seq[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)]
      = forgingStakeMerklePathInfoSeq.view.flatMap(forgingStakeMerklePathInfo => getSecretsAndProof(nodeView.vault, vrfMessage, forgingStakeMerklePathInfo))

      val eligibleForgingDataView: Seq[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)]
      = ownedForgingDataView.filter { case (forgingStakeMerklePathInfo, _, _, vrfOutput) =>
        vrfProofCheckAgainstStake(vrfOutput, forgingStakeMerklePathInfo.forgingStakeInfo.stakeAmount, totalStake)
      }


      val eligibleForgerOpt = eligibleForgingDataView.headOption //force all forging related calculations

      val forgingResult = eligibleForgerOpt
        .map { case (forgingStakeMerklePathInfo, privateKey25519, vrfProof, _) =>
          forgeBlock(nodeView, nextBlockTimestamp, branchPointInfo, forgingStakeMerklePathInfo, privateKey25519, vrfProof, timeout)
        }
        .getOrElse(SkipSlot("No eligible forging stake found."))
      forgingResult
    }
  } match {
    case Success(result) => {
      log.info(s"Forge result is: $result")
      result
    }
    case Failure(ex) => {
      log.error(s"Failed to forge block for ${nextConsensusEpochNumber} epoch ${nextConsensusSlotNumber} slot due:", ex)
      ForgeFailed(ex)
    }
  }

  protected def getSecretsAndProof(
                     wallet: VL,
                     vrfMessage: VrfMessage,
                     forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo): Option[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)] =
  {
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

  protected def checkNextEpochAndSlot(parentBlockTimestamp: Long,
                                    currentTipBlockTimestamp: Long,
                                    nextEpochNumber: ConsensusEpochNumber,
                                    nextSlotNumber: ConsensusSlotNumber): Option[ForgeFailure] = {
    // Parent block and current tip block can be the same in case of extension the Active chain.
    // But can be different in case of sidechain fork caused by mainchain fork.
    // In this case parent block is before the tip, and tip block will be the last Ommer included into the next block.
    val parentBlockEpochAndSlot: ConsensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, parentBlockTimestamp)
    val currentTipBlockEpochAndSlot: ConsensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, currentTipBlockTimestamp)
    val nextBlockEpochAndSlot: ConsensusEpochAndSlot = ConsensusEpochAndSlot(nextEpochNumber, nextSlotNumber)

    if(parentBlockEpochAndSlot > nextBlockEpochAndSlot) {
      return Some(ForgeFailed(new IllegalArgumentException (s"Try to forge block with incorrect epochAndSlot $nextBlockEpochAndSlot which are equal or less than parent block epochAndSlot: $parentBlockEpochAndSlot")))
    }

    if(parentBlockEpochAndSlot == nextBlockEpochAndSlot) {
      return Some(SkipSlot(s"Chain tip with $nextBlockEpochAndSlot has been generated already."))
    }

    if ((nextEpochNumber - parentBlockEpochAndSlot.epochNumber) > 1) {
      return Some(ForgeFailed(new IllegalArgumentException (s"Forging is not possible, because of whole consensus epoch is missed: current epoch = $nextEpochNumber, parent epoch = ${parentBlockEpochAndSlot.epochNumber}")))
    }

    if(currentTipBlockEpochAndSlot >= nextBlockEpochAndSlot) {
      return Some(ForgeFailed(new IllegalArgumentException (s"Try to forge block with incorrect epochAndSlot $nextBlockEpochAndSlot which are equal or less than last ommer epochAndSlot: $currentTipBlockEpochAndSlot")))
    }

    None
  }

  protected def getBranchPointInfo(history: HIS): Try[BranchPointInfo] = Try {
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
        throw new Exception("No sense to forge: active branch contains orphaned MainchainHeaders, that number is greater or equal to actual new MainchainHeaders.")
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

  protected def forgeBlock(nodeView: View,
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
    blockSize += 4 + 4 // placeholder for the MainchainReferenceData and Transactions sequences size values

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
    var ommers: Seq[Ommer[H]] = Seq()
    var blockId = nodeView.history.bestBlockId
    while (blockId != branchPointInfo.branchPointId) {
      val block = nodeView.history.getBlockById(blockId).get() // TODO: replace with method blockById with no Option
      blockId = block.parentId
      ommers = Ommer.toOmmer(block) +: ommers
    }

    // Update block size with Ommers
    //val ommersSerializer = new ListSerializer[Ommer[H]](OmmerSerializer)
    //blockSize += ommersSerializer.toBytes(ommers.asJava).length
    blockSize += getOmmersSize(ommers)

    // Get all needed MainchainBlockReferences from the MC Node
    // Extract MainchainReferenceData and collect as much as the block can fit
    val mainchainBlockReferenceDataToRetrieve: Seq[MainchainHeaderHash] = branchPointInfo.referenceDataToInclude

    val mainchainReferenceData: ArrayBuffer[MainchainBlockReferenceData] = ArrayBuffer()
    // Collect MainchainRefData considering the actor message processing timeout
    // Note: We may do a lot of websocket `getMainchainBlockReference` operations that are a bit slow,
    // because they are processed one by one, so we limit requests in time.
    val startTime: Long = System.currentTimeMillis()
    mainchainBlockReferenceDataToRetrieve.takeWhile(hash => {
      mainchainSynchronizer.getMainchainBlockReference(hash) match {
        case Success(ref) => {
          val refDataSize = ref.data.bytes.length + 4 // placeholder for MainchainReferenceData length
          if(blockSize + refDataSize > SidechainBlockBase.MAX_BLOCK_SIZE)
            false // stop data collection
          else {
            mainchainReferenceData.append(ref.data)
            blockSize += refDataSize
            // Note: temporary solution because of the delays on MC Websocket server part.
            // Can be after MC Websocket performance optimization.
            val isTimeout: Boolean = System.currentTimeMillis() - startTime >= timeout.duration.toMillis / 2
            !isTimeout // continue data collection
          }
        }
        case Failure(ex) => return ForgeFailed(ex)
      }
    })

    val isWithdrawalEpochLastBlock: Boolean = mainchainReferenceData.size == withdrawalEpochMcBlocksLeft

    // Collect transactions if possible
    val transactions: Seq[TX] = collectTransactionsFromMemPool(nodeView, isWithdrawalEpochLastBlock, blockSize)

    val tryBlock = createNewBlock(
      nodeView,
      branchPointInfo,
      isWithdrawalEpochLastBlock,
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

  def createNewBlock(
                     nodeView: View,
                     branchPointInfo: BranchPointInfo,
                     isWithdrawalEpochLastBlock: Boolean,
                     parentBlockId: Block.BlockId,
                     timestamp: Block.Timestamp,
                     mainchainReferenceData: Seq[MainchainBlockReferenceData],
                     sidechainTransactions: Seq[Transaction],
                     mainchainHeaders: Seq[MainchainHeader],
                     ommers: Seq[Ommer[H]],
                     blockSignPrivateKey: PrivateKey25519,
                     forgingStakeInfo: ForgingStakeInfo,
                     vrfProof: VrfProof,
                     forgingStakeInfoMerklePath: MerklePath,
                     companion: DynamicTypedSerializer[TX,  TransactionSerializer[TX]],
                     signatureOption: Option[Signature25519] = None
                    ): Try[SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase]]


  def precalculateBlockHeaderSize(
                      parentId: ModifierId,
                      timestamp: Long,
                      forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                      vrfProof: VrfProof): Int

  def collectTransactionsFromMemPool(
                      nodeView: View,
                      isWithdrawalEpochLastBlock: Boolean,
                      blockSize: Int) : Seq[TX]


  def getOmmersSize(ommers: Seq[Ommer[H]]) : Int

  def getForgingStakeMerklePathInfo(nextConsensusEpochNumber: consensus.ConsensusEpochNumber, wallet: VL) : Seq[ForgingStakeMerklePathInfo]
}





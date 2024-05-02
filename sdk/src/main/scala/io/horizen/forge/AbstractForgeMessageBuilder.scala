package io.horizen.forge

import akka.util.Timeout
import io.horizen.block._
import io.horizen.chain.{AbstractFeePaymentsInfo, MainchainHeaderHash, SidechainBlockInfo}
import io.horizen.consensus._
import io.horizen.fork.{ActiveSlotCoefficientFork, ForkManager}
import io.horizen.history.AbstractHistory
import io.horizen.metrics.MetricsManager
import io.horizen.params.{NetworkParams, RegTestParams}
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.secret.{PrivateKey25519, VrfSecretKey}
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.{Transaction, TransactionSerializer}
import io.horizen.utils.{DynamicTypedSerializer, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, TimeToEpochUtils, WithdrawalEpochInfo}
import io.horizen.vrf.VrfOutput
import io.horizen.wallet.AbstractWallet
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.block.Block
import sparkz.core.transaction.MemoryPool
import sparkz.core.transaction.state.MinimalState
import sparkz.util.{ModifierId, SparkzLogging}

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
    allowNoWebsocketConnectionInRegtest: Boolean) extends SparkzLogging
{
  type FPI <: AbstractFeePaymentsInfo
  type HSTOR <: AbstractHistoryStorage[PM, FPI, HSTOR]
  type HIS <: AbstractHistory[TX, H, PM, FPI, HSTOR, HIS]
  type VL <: AbstractWallet[TX, PM, VL]
  type MS <: MinimalState[PM, MS]
  type MP <: MemoryPool[TX, MP]

  type View = CurrentView[HIS, MS, VL, MP]

  type ForgeMessageType = GetDataFromCurrentView[ HIS,  MS,  VL,  MP, ForgeResult]

  val metricsManager:MetricsManager = MetricsManager.getInstance()

  def buildForgeMessageForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber, mcRefDataRetrievalTimeout: Timeout, forcedTx: Iterable[TX]): ForgeMessageType = {
    val forgingFunctionForEpochAndSlot: View => ForgeResult = tryToForgeNextBlock(consensusEpochNumber, consensusSlotNumber, mcRefDataRetrievalTimeout, forcedTx)

    val forgeMessage: ForgeMessageType =
      GetDataFromCurrentView[ HIS,  MS,  VL,  MP, ForgeResult](forgingFunctionForEpochAndSlot)

    forgeMessage
  }

  case class BranchPointInfo(branchPointId: ModifierId, referenceDataToInclude: Seq[MainchainHeaderHash], headersToInclude: Seq[MainchainHeaderHash])

  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber, mcRefDataRetrievalTimeout: Timeout, forcedTx: Iterable[TX])(nodeView: View): ForgeResult = Try {
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

    val lotteryStart = metricsManager.currentMillis();

    val nextBlockTimestamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params.sidechainGenesisBlockTimestamp, nextConsensusEpochNumber, nextConsensusSlotNumber)
    val consensusInfo: FullConsensusEpochInfo = nodeView.history.getFullConsensusEpochInfoForBlock(nextBlockTimestamp, parentBlockId)
    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)

    // Get ForgingStakeMerklePathInfo from wallet and order them by stake decreasing.
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] = getForgingStakeMerklePathInfo(
      nextConsensusEpochNumber, nodeView.vault, nodeView.history, nodeView.state, branchPointInfo, nextBlockTimestamp)
      .sortWith(_.forgingStakeInfo.stakeAmount > _.forgingStakeInfo.stakeAmount)

    if (forgingStakeMerklePathInfoSeq.isEmpty) {
      NoOwnedForgingStake
    } else {
      val ownedForgingDataView: Seq[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)]
      = forgingStakeMerklePathInfoSeq.view.flatMap(forgingStakeMerklePathInfo => getSecretsAndProof(nodeView.vault, vrfMessage, forgingStakeMerklePathInfo))

      val percentageForkApplied = ForkManager.getSidechainFork(nextConsensusEpochNumber).stakePercentageForkApplied
      val activeSlotCoefficient = ActiveSlotCoefficientFork.get(nextConsensusEpochNumber).activeSlotCoefficient
      val eligibleForgingDataView: Seq[(ForgingStakeMerklePathInfo, PrivateKey25519, VrfProof, VrfOutput)]
      = ownedForgingDataView.filter { case (forgingStakeMerklePathInfo, _, _, vrfOutput) =>
        vrfProofCheckAgainstStake(vrfOutput, forgingStakeMerklePathInfo.forgingStakeInfo.stakeAmount, totalStake, percentageForkApplied, activeSlotCoefficient)
      }


      val eligibleForgerOpt = eligibleForgingDataView.headOption //force all forging related calculations

      metricsManager.lotteryDone(metricsManager.currentMillis() - lotteryStart)

      val forgingResult = eligibleForgerOpt
        .map { case (forgingStakeMerklePathInfo, privateKey25519, vrfProof, vrfOutput) =>
          forgeBlock(
            nodeView,
            nextBlockTimestamp,
            branchPointInfo,
            forgingStakeMerklePathInfo,
            privateKey25519,
            vrfProof,
            vrfOutput,
            mcRefDataRetrievalTimeout,
            forcedTx
          )
        }
        .getOrElse(SkipSlot("No eligible forging stake found."))
      forgingResult
    }
  } match {
    case Success(result) =>
      log.info(s"Forge result is: $result")
      result
    case Failure(ex) =>
      log.error(s"Failed to forge block for $nextConsensusEpochNumber epoch $nextConsensusSlotNumber slot due:", ex)
      ForgeFailed(ex)
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
    val parentBlockEpochAndSlot: ConsensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params.sidechainGenesisBlockTimestamp, parentBlockTimestamp)
    val currentTipBlockEpochAndSlot: ConsensusEpochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params.sidechainGenesisBlockTimestamp, currentTipBlockTimestamp)
    val nextBlockEpochAndSlot: ConsensusEpochAndSlot = ConsensusEpochAndSlot(nextEpochNumber, nextSlotNumber)

    if ((nextEpochNumber - currentTipBlockEpochAndSlot.epochNumber) > 1) {
      return Some(SkipSlot(s"Chain tip with epoch ${currentTipBlockEpochAndSlot.epochNumber} is too far in past: next block epoch=${nextBlockEpochAndSlot.epochNumber}/slot=${nextBlockEpochAndSlot.slotNumber} (it is OK if we are syncing the node)"))
    }

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

    var (bestMainchainCommonPointHeight: Int, bestMainchainCommonPointHash: MainchainHeaderHash, newHeaderHashes: Seq[MainchainHeaderHash]) =
      mainchainSynchronizer.getMainchainDivergentSuffix(history, MainchainSynchronizer.MAX_BLOCKS_REQUEST) match {
        case Success((height, hashes)) => (height, hashes.head, hashes.tail) // hashes contains also the hash of best known block
        case Failure(ex) =>
          // For regtest Forger is allowed to produce next block in case if there is no MC Node connection
          if (params.isInstanceOf[RegTestParams] && allowNoWebsocketConnectionInRegtest)
            (bestMainchainHeaderInfo.height, bestMainchainHeaderInfo.hash, Seq())
          else
            throw ex
      }

    newHeaderHashes = if(newHeaderHashes.nonEmpty && newHeaderHashes.size > params.mcBlockRefDelay) newHeaderHashes.take(newHeaderHashes.size - params.mcBlockRefDelay) else Seq()

    // Check that there is no orphaned mainchain headers: SC most recent mainchain header is a part of MC active chain
    if(bestMainchainCommonPointHash == bestMainchainHeaderInfo.hash) {
      val branchPointId: ModifierId = history.bestBlockId
      var withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength - history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex
      if (withdrawalEpochMcBlocksLeft == 0) // current best block is the last block of the epoch
        withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

      val missedMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = history.missedMainchainReferenceDataHeaderHashes
      val nextMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = missedMainchainReferenceDataHeaderHashes ++ newHeaderHashes

      // to not to include mcblock references data from different withdrawal epochs
      val maxReferenceDataNumber: Int = Math.min(withdrawalEpochMcBlocksLeft, nextMainchainReferenceDataHeaderHashes.size)

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

  // the max size of the block excluding txs
  def getMaxBlockOverheadSize: Int
  // the max size of the block including txs
  def getMaxBlockSize: Int

  protected def forgeBlock(nodeView: View,
                           timestamp: Long,
                           branchPointInfo: BranchPointInfo,
                           forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                           blockSignPrivateKey: PrivateKey25519,
                           vrfProof: VrfProof,
                           vrfOutput: VrfOutput,
                           mcRefDataRetrievalTimeout: Timeout,
                           forcedTx: Iterable[TX],
                           isPending: Boolean = false): ForgeResult = {
    log.info("Start forging the next block...")
    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    val parentBlockInfo: SidechainBlockInfo = nodeView.history.blockInfoById(parentBlockId)
    var withdrawalEpochMcBlocksLeft: Int = params.withdrawalEpochLength - parentBlockInfo.withdrawalEpochInfo.lastEpochIndex
    if (withdrawalEpochMcBlocksLeft == 0) // parent block is the last block of the epoch
      withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

    var blockSize: Int = precalculateBlockHeaderSize(parentBlockId, timestamp, forgingStakeMerklePathInfo, vrfProof)
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
    while (blockId != parentBlockId) {
      val block = nodeView.history.getBlockById(blockId).get
      blockId = block.parentId
      ommers = Ommer.toOmmer(block) +: ommers
    }

    // Update block size with Ommers
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
        case Success(ref) =>
          val refDataSize = ref.data.bytes.length + 4 // placeholder for MainchainReferenceData length
          if (blockSize + refDataSize > getMaxBlockOverheadSize) {
            log.info(s"Block size would exceed limit, stopping mc ref data collection. Block size $blockSize, Data collected so far: ${mainchainReferenceData.length}, refData skipped size: $refDataSize")
            false // stop data collection
          } else {
            mainchainReferenceData.append(ref.data)
            blockSize += refDataSize
            // Note: temporary solution because of the delays on MC Websocket server part.
            // Can be after MC Websocket performance optimization.
            val isTimeout: Boolean = System.currentTimeMillis() - startTime >= mcRefDataRetrievalTimeout.duration.toMillis
            !isTimeout // continue data collection
          }
        case Failure(ex) => return ForgeFailed(ex)
      }
    })

    // if we have no mc block ref, we must ensure we are not creating too long a chain without mc ref blocks
    val consensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params.sidechainGenesisBlockTimestamp, timestamp)
    if (nodeView.history.tooManyBlocksWithoutMcHeaders(branchPointInfo.branchPointId, mainchainHeaders.isEmpty, consensusEpochNumber)) {
      return SkipSlot(s"We can not forge until we have at least a mc block reference included, skipping this slot...")
    }

    val isWithdrawalEpochLastBlock: Boolean = mainchainReferenceData.size == withdrawalEpochMcBlocksLeft

    val transactions: Iterable[TX] = if (isWithdrawalEpochLastBlock) {
      Iterable.empty[TX] // no SC Txs allowed
    } else if (parentBlockId != nodeView.history.bestBlockId) {
      // SC block extends the block behind the current tip (for example, in case of ommers).
      // We can't be sure that transactions in the Mempool are valid against the block in the past.
      // For example the ommerred Block contains Tx which output is going to be spent by another Tx in the Mempool.
      Iterable.empty[TX]
    } else {
      collectTransactionsFromMemPool(nodeView, blockSize, mainchainReferenceData, parentBlockInfo.withdrawalEpochInfo, timestamp, forcedTx)
    }

    log.trace(s"Transactions to apply $transactions")
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
      vrfOutput,
      forgingStakeMerklePathInfo.merklePath,
      companion,
      blockSize,
      isPending = isPending
    )

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
                     sidechainTransactions: Iterable[TX],
                     mainchainHeaders: Seq[MainchainHeader],
                     ommers: Seq[Ommer[H]],
                     blockSignPrivateKey: PrivateKey25519,
                     forgingStakeInfo: ForgingStakeInfo,
                     vrfProof: VrfProof,
                     vrfOutput: VrfOutput,
                     forgingStakeInfoMerklePath: MerklePath,
                     companion: DynamicTypedSerializer[TX,  TransactionSerializer[TX]],
                     inputBlockSize: Int,
                     signatureOption: Option[Signature25519] = None,
                     isPending: Boolean = false
                    ): Try[SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase]]


  def precalculateBlockHeaderSize(
                      parentId: ModifierId,
                      timestamp: Long,
                      forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                      vrfProof: VrfProof): Int

  def collectTransactionsFromMemPool(nodeView: View, blockSizeIn: Int,
                                     mainchainBlockReferenceData: Seq[MainchainBlockReferenceData],
                                     withdrawalEpochInfo: WithdrawalEpochInfo,
                                     timestamp: Long,
                                     forcedTx: Iterable[TX]): Iterable[TX]

  def getOmmersSize(ommers: Seq[Ommer[H]]) : Int

  def getForgingStakeMerklePathInfo(nextConsensusEpochNumber: ConsensusEpochNumber, wallet: VL, history: HIS, state: MS, branchPointInfo: BranchPointInfo, nextBlockTimestamp: Long): Seq[ForgingStakeMerklePathInfo]
}





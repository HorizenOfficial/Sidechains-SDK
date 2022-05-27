package com.horizen.forge

import akka.util.Timeout
import com.horizen.block._
import com.horizen.box.Box
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, VrfSecretKey}
import com.horizen.storage.{AbstractHistoryStorage, SidechainHistoryStorage}
import com.horizen.transaction.{SidechainTransaction, Transaction, TransactionSerializer}
import com.horizen.utils.{DynamicTypedSerializer, FeePaymentsUtils, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree, TimeToEpochUtils}
import com.horizen.{AbstractHistory, AbstractWallet, SidechainHistory, SidechainMemoryPool, SidechainState, SidechainTypes, SidechainWallet}
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.JavaConverters._
import com.horizen.vrf.VrfOutput
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewModifier
import scorex.core.block.Block
import scorex.core.block.Block.{BlockId, Timestamp}
import scorex.core.transaction.MemoryPool
import scorex.core.transaction.state.MinimalState

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

class ForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                          companion: SidechainTransactionsCompanion,
                          params: NetworkParams,
                          allowNoWebsocketConnectionInRegtest: Boolean)
  extends AbstractForgeMessageBuilder[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock](
  mainchainSynchronizer, companion, params, allowNoWebsocketConnectionInRegtest
) {
  type HSTOR = SidechainHistoryStorage
  type VL = SidechainWallet
  type HIS = SidechainHistory
  type MS = SidechainState
  type MP = SidechainMemoryPool

  type ForgeMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult]

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

    checkNextEpochAndSlot(parentBlockInfo.timestamp, nodeView.history.bestBlockInfo.timestamp,
      nextConsensusEpochNumber, nextConsensusSlotNumber) match {
      case Some(forgeFailure) => return forgeFailure
      case _ => // checks passed
    }

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

  override def precalculateBlockHeaderSize(parentId: ModifierId,
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
      new Array[Byte](NodeViewModifier.ModifierIdSize),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    header.bytes.length
  }

  override def createNewBlock(
                               parentId: BlockId, timestamp: Timestamp, mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                               sidechainTransactions: Seq[Transaction], mainchainHeaders: Seq[MainchainHeader],
                               ommers: Seq[Ommer[SidechainBlockHeader]], ownerPrivateKey: PrivateKey25519, forgingStakeInfo: ForgingStakeInfo,
                               vrfProof: VrfProof, forgingStakeInfoMerklePath: MerklePath, feePaymentsHash: Array[Byte],
                               companion: DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]],
                               signatureOption: Option[Signature25519]): Try[SidechainBlockBase[SidechainTypes#SCBT, SidechainBlockHeader]] = {
    SidechainBlock.create(
      parentId,
      SidechainBlock.BLOCK_VERSION,
      timestamp,
      mainchainBlockReferencesData,
      // TODO check, why this works?
      //  sidechainTransactions.map(asInstanceOf),
      sidechainTransactions.map(x => x.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]),
      mainchainHeaders,
      ommers,
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      forgingStakeInfoMerklePath,
      feePaymentsHash,
      // TODO check, why this works?
      //companion.asInstanceOf)
      companion.asInstanceOf[SidechainTransactionsCompanion])
  }

  override def collectTransactionsFromMemPool(nodeView: View, isWithdrawalEpochLastBlock: Boolean, blockSizeIn: Int): Seq[SidechainTypes#SCBT] = {

    var blockSize: Int = blockSizeIn
    if (isWithdrawalEpochLastBlock) { // SC block is going to become the last block of the withdrawal epoch
      Seq() // no SC Txs allowed
    } else { // SC block is in the middle of the epoch
      var txsCounter: Int = 0
      nodeView.pool.take(nodeView.pool.size).filter(tx => {
        val txSize = tx.bytes.length + 4 // placeholder for Tx length
        txsCounter += 1
        if (txsCounter > SidechainBlockBase.MAX_SIDECHAIN_TXS_NUMBER || blockSize + txSize > SidechainBlockBase.MAX_BLOCK_SIZE)
          false // stop data collection
        else {
          blockSize += txSize
          true // continue data collection
        }
      }).toSeq
    }
  }

  override def getOmmersSize(ommers: Seq[Ommer[SidechainBlockHeader]]): Int = {
    val ommersSerializer = new ListSerializer[Ommer[SidechainBlockHeader]](OmmerSerializer)
    ommersSerializer.toBytes(ommers.asJava).length
  }

  override def forgeBlock(nodeView: View,
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
    var ommers: Seq[Ommer[SidechainBlockHeader]] = Seq()
    var blockId = nodeView.history.bestBlockId
    while (blockId != branchPointInfo.branchPointId) {
      val block = nodeView.history.getBlockById(blockId).get() // TODO: replace with method blockById with no Option
      blockId = block.parentId
      ommers = Ommer.toOmmer(block) +: ommers
    }

    // Update block size with Ommers
    val ommersSerializer = new ListSerializer[Ommer[SidechainBlockHeader]](OmmerSerializer)
    blockSize += ommersSerializer.toBytes(ommers.asJava).length

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
    val transactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]] =
      if (isWithdrawalEpochLastBlock) { // SC block is going to become the last block of the withdrawal epoch
        Seq() // no SC Txs allowed
      } else { // SC block is in the middle of the epoch
        var txsCounter: Int = 0
        nodeView.pool.take(nodeView.pool.size).filter(tx => {
          val txSize = tx.bytes.length + 4 // placeholder for Tx length
          txsCounter += 1
          if(txsCounter > SidechainBlockBase.MAX_SIDECHAIN_TXS_NUMBER || blockSize + txSize > SidechainBlockBase.MAX_BLOCK_SIZE)
            false // stop data collection
          else {
            blockSize += txSize
            true // continue data collection
          }
        }).map(tx => tx.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]).toSeq // TO DO: problems with types
      }

    val feePayments = if(isWithdrawalEpochLastBlock) {
      // Current block is expect to be the continuation of the current tip, so there are no ommers.
      require(nodeView.history.bestBlockId == branchPointInfo.branchPointId, "Last block of the withdrawal epoch expect to be a continuation of the tip.")
      require(ommers.isEmpty, "No Ommers allowed for the last block of the withdrawal epoch.")

      val withdrawalEpochNumber: Int = nodeView.state.getWithdrawalEpochInfo.epoch

      val forgedBlockFeeInfo = SidechainBlock.create(
        parentBlockId,
        SidechainBlock.BLOCK_VERSION,
        timestamp,
        mainchainReferenceData,
        transactions,
        mainchainHeaders,
        ommers,
        blockSignPrivateKey,
        forgingStakeMerklePathInfo.forgingStakeInfo,
        vrfProof,
        forgingStakeMerklePathInfo.merklePath,
        new Array[Byte](32), // dummy feePaymentsHash value
        companion) match {
        case Success(blockTemplate) => blockTemplate.feeInfo
        case Failure(exception) => return ForgeFailed(exception)
      }

      nodeView.state.getFeePayments(withdrawalEpochNumber, Some(forgedBlockFeeInfo))
    } else {
      Seq()
    }

    val feePaymentsHash: Array[Byte] = FeePaymentsUtils.calculateFeePaymentsHash(feePayments)

    val tryBlock = SidechainBlock.create(
      parentBlockId,
      SidechainBlock.BLOCK_VERSION,
      timestamp,
      mainchainReferenceData,
      transactions,
      mainchainHeaders,
      ommers,
      blockSignPrivateKey,
      forgingStakeMerklePathInfo.forgingStakeInfo,
      vrfProof,
      forgingStakeMerklePathInfo.merklePath,
      feePaymentsHash,
      companion)

    tryBlock match {
      case Success(block) => ForgeSuccess(block)
      case Failure(exception) => ForgeFailed(exception)
    }
  }

}





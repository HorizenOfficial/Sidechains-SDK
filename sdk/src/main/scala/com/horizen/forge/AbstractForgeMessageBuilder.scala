package com.horizen.forge

import akka.util.Timeout
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.block._
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.{PrivateKey25519, VrfSecretKey}
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.{Transaction, TransactionSerializer}
import com.horizen.utils.{BlockFeeInfo, DynamicTypedSerializer, FeePaymentsUtils, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree, TimeToEpochUtils}
import com.horizen.vrf.VrfOutput
import com.horizen.{AbstractHistory, AbstractWallet}
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.block.Block
import scorex.core.transaction.MemoryPool
import scorex.core.transaction.state.MinimalState
import scorex.util.{ModifierId, ScorexLogging, idToBytes}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

abstract class AbstractForgeMessageBuilder[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  HSTOR <: AbstractHistoryStorage[PM, HSTOR],
  VL <: AbstractWallet[TX, PM, VL],
  HIS <: AbstractHistory[TX, H, PM, HSTOR, HIS]] (
    mainchainSynchronizer: MainchainSynchronizer,
    companion: DynamicTypedSerializer[TX,  TransactionSerializer[TX]],
    val params: NetworkParams,
    allowNoWebsocketConnectionInRegtest: Boolean) extends ScorexLogging {

  /*
  type MS <: MinimalState[PM, MS]
  type MP <: MemoryPool[TX, MP]
  type View = CurrentView[HIS, MS, VL, MP]
   */

  case class BranchPointInfo(branchPointId: ModifierId, referenceDataToInclude: Seq[MainchainHeaderHash], headersToInclude: Seq[MainchainHeaderHash])


  protected def getSecretsAndProof(wallet: AbstractWallet[TX, PM, VL],
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

  protected def precalculateBlockHeaderSize(parentId: ModifierId,
                                          timestamp: Long,
                                          forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                                          vrfProof: VrfProof): Int


  def getOmmersSize(ommers: Seq[Ommer[H]]) : Int

  protected def forgeBlock(nodeView: View,
                           timestamp: Long,
                           branchPointInfo: BranchPointInfo,
                           forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                           blockSignPrivateKey: PrivateKey25519,
                           vrfProof: VrfProof,
                           timeout: Timeout): ForgeResult

 def createNewBlock( parentId: Block.BlockId,
  timestamp: Block.Timestamp,
  mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
  sidechainTransactions: Seq[Transaction],
  mainchainHeaders: Seq[MainchainHeader],
  ommers: Seq[Ommer[H]],
  ownerPrivateKey: PrivateKey25519,
  forgingStakeInfo: ForgingStakeInfo,
  vrfProof: VrfProof,
  forgingStakeInfoMerklePath: MerklePath,
  feePaymentsHash: Array[Byte],
  companion: DynamicTypedSerializer[TX,  TransactionSerializer[TX]],
  signatureOption: Option[Signature25519] = None
  ): Try[SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase]]

  def collectTransactionsFromMemPool(nodeView: View, isWithdrawalEpochLastBlock: Boolean, blockSize: Int) : Seq[TX]

  def getWithdrawalEpochNumber(nodeView: View) : Int = ???

  def getFeePaymentsHash(
    nodeView: View,
    branchPointInfo: BranchPointInfo,
    parentId: Block.BlockId,
    isWithdrawalEpochLastBlock: Boolean,
    timestamp: Block.Timestamp,
    mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
    sidechainTransactions: Seq[Transaction],
    mainchainHeaders: Seq[MainchainHeader],
    ommers: Seq[Ommer[H]],
    ownerPrivateKey: PrivateKey25519,
    forgingStakeInfo: ForgingStakeInfo,
    vrfProof: VrfProof,
    forgingStakeInfoMerklePath: MerklePath,
    feePaymentsHash: Array[Byte],
    companion: DynamicTypedSerializer[TX,  TransactionSerializer[TX]]
  ) : Array[Byte] = ???

}





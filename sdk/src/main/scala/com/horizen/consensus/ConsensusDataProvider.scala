package com.horizen.consensus

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.proof.VrfProof
import com.horizen.storage.SidechainBlockInfoProvider
import com.horizen.utils.Utils
import com.horizen.vrf.VrfProofHash
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import scorex.util.{ModifierId, ScorexLogging}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

trait ConsensusDataProvider {
  this: TimeToEpochSlotConverter
    with NetworkParamsUtils
    with ScorexLogging {
    val storage: SidechainBlockInfoProvider
    val consensusDataStorage: ConsensusDataStorage
    val params: NetworkParams
  } =>

  def getFullConsensusEpochInfoForBlock(blockId: ModifierId, blockInfo: SidechainBlockInfo): FullConsensusEpochInfo = {
    log.debug(s"Requested FullConsensusEpochInfo for ${blockId} block id")

    val previousEpochId = getPreviousConsensusEpochIdForBlock(blockId, blockInfo)
    getFullConsensusEpochInfoByEpochId(previousEpochId)
  }

  def getFullConsensusEpochInfoForNextBlock(currentBlockId: ModifierId, nextBlockConsensusEpochNumber: ConsensusEpochNumber): FullConsensusEpochInfo = {
    val currentBlockInfo = storage.blockInfoById(currentBlockId)
    val currentBlockEpochNumber: ConsensusEpochNumber = timeStampToEpochNumber(currentBlockInfo.timestamp)
    val currentBlockIsLastInEpoch = nextBlockConsensusEpochNumber > currentBlockEpochNumber

    if (currentBlockIsLastInEpoch) {
      //if block is last in epoch then consensus epochId is equals to blockId
      getFullConsensusEpochInfoByEpochId(blockIdToEpochId(currentBlockId))
    }
    else {
      getFullConsensusEpochInfoForBlock(currentBlockId, currentBlockInfo)
    }
  }

  def getFullConsensusEpochInfoByEpochId(epochId: ConsensusEpochId): FullConsensusEpochInfo = {
    val nonceEpochInfo: NonceConsensusEpochInfo = consensusDataStorage.getNonceConsensusEpochInfo(epochId).getOrElse(calculateNonceForEpoch(epochId))


    val lastBlockIdInPreviousEpoch = lastBlockIdInEpochId(epochId)
    val prePreviousEpochId: ConsensusEpochId = getPreviousConsensusEpochIdForBlock(lastBlockIdInPreviousEpoch, storage.blockInfoById(lastBlockIdInPreviousEpoch))
    val stakeEpochInfo: StakeConsensusEpochInfo =
      consensusDataStorage
        .getStakeConsensusEpochInfo(prePreviousEpochId)
        .getOrElse(throw new IllegalStateException(s"Stake was not defined for epoch ${prePreviousEpochId}"))

    FullConsensusEpochInfo(stakeEpochInfo, nonceEpochInfo)
  }

  //Check possible stack buffer overflow situations
  def calculateNonceForEpoch(epochId: ConsensusEpochId): NonceConsensusEpochInfo = {
    val lastBlockIdInEpoch: ModifierId = lastBlockIdInEpochId(epochId)
    val lastBlockInfoInEpoch: SidechainBlockInfo = storage.blockInfoById(lastBlockIdInEpoch)

    if (isGenesisBlock(lastBlockIdInEpoch)) {
      ConsensusDataProvider.calculateNonceForGenesisBlockInfo(lastBlockInfoInEpoch)
    }
    else {
      calculateNonceForNonGenesisEpoch(lastBlockIdInEpoch, lastBlockInfoInEpoch)
    }
  }

  private def calculateNonceForNonGenesisEpoch(lastBlockIdInEpoch: ModifierId, lastBlockInfoInEpoch: SidechainBlockInfo): NonceConsensusEpochInfo = {
    val previousEpoch = getPreviousConsensusEpochIdForBlock(lastBlockIdInEpoch, storage.blockInfoById(lastBlockIdInEpoch))

    val allVrfOutputsWithSlots =
      foldEpochRight[ListBuffer[(VrfProof, VrfProofHash, ConsensusSlotNumber)]](ListBuffer(), lastBlockIdInEpoch, lastBlockInfoInEpoch) {
      (blockId, blockInfo, accumulator) =>
        (blockInfo.vrfProof, blockInfo.vrfProofHash, timeStampToSlotNumber(blockInfo.timestamp)) +=: accumulator
    }.toList

    //Hash function is applied to the concatenation of VRF values that are inserted into each block, using values from
    //all blocks up to and including the middle ≈ 8k slots of an epoch that lasts approximately 24k slots in entirety
    // https://eprint.iacr.org/2017/573.pdf p.23
    val quietSlotsNumber = params.consensusSlotsInEpoch / 3
    val eligibleSlotsRange = (quietSlotsNumber to params.consensusSlotsInEpoch - quietSlotsNumber)
    val eligibleForNonceCalculation =
      allVrfOutputsWithSlots
        .withFilter{case (_, _, slot) => eligibleSlotsRange.contains(slot)}
        .map{case (proof,proofHash,  _) => ByteUtils.concatenate(proof.bytes(), proofHash.bytes())}

    //According to https://eprint.iacr.org/2017/573.pdf p.26
    val previousNonce = consensusDataStorage.getNonceConsensusEpochInfo(previousEpoch).getOrElse(calculateNonceForEpoch(previousEpoch)).bytes
    val currentEpochNumberBytes = Ints.toByteArray(timeStampToEpochNumber(lastBlockInfoInEpoch.timestamp))

    val allNonceBytesAsList = previousNonce :: currentEpochNumberBytes :: eligibleForNonceCalculation
    val newNonce = Utils.doubleSHA256Hash(Bytes.concat(allNonceBytesAsList:_*)) // :_* -- converts list to varargs

    NonceConsensusEpochInfo(byteArrayToConsensusNonce(newNonce))
  }



  /**
   * @return Return last block in previous epoch, for genesis block last block of previous epoch is genesis block itself
   */
  private def getPreviousConsensusEpochIdForBlock(blockId: ModifierId, blockInfo: SidechainBlockInfo): ConsensusEpochId = {
    if (isGenesisBlock(blockId)) {
      blockIdToEpochId(blockId)
    }
    else {
      val lastBlockId = foldEpochRight(blockInfo.parentId, blockId, blockInfo)((_, blockInfo, _) => blockInfo.parentId)
      blockIdToEpochId(lastBlockId)
    }
  }

  /**
   * Perform folding right on whole epoch, i.e. apply op function on every Sidechain block starting from given block in epoch to a start of the epoch
   * @param accumulator initial value
   * @param blockId start point for folding, not necessary to be a real last block in consensus epoch
   * @param blockInfo appropriate blockInfo for blockId
   * @param op operation on blockInfo in consensus epoch
   * @tparam A type of accumulator
   * @return result of performed operations on blocks in epochs
   */
  private def foldEpochRight[A](accumulator: A, blockId: ModifierId, blockInfo: SidechainBlockInfo)(op: (ModifierId, SidechainBlockInfo, A) => A): A = {
    @tailrec
    def foldEpochIteration[B](accumulator: B, blockId: ModifierId, blockInfo: SidechainBlockInfo, processedEpochNumber: ConsensusEpochNumber)
                             (op: (ModifierId, SidechainBlockInfo, B) => B): B = {
      val blockEpochNumber = timeStampToEpochNumber(blockInfo.timestamp)
      if (blockEpochNumber < processedEpochNumber) {
        accumulator
      }
      else {
        if (isGenesisBlock(blockId)) {
          op(blockId, blockInfo, accumulator)
        }
        else {
          require(processedEpochNumber == blockEpochNumber)

          val currentBlockOpResult = op(blockId, blockInfo, accumulator)
          val parentId = blockInfo.parentId
          val parentBlockInfo = storage.blockInfoById(parentId)
          foldEpochIteration(currentBlockOpResult, parentId, parentBlockInfo, processedEpochNumber)(op)
        }
      }
    }

    val epochNumber = timeStampToEpochNumber(blockInfo.timestamp)
    foldEpochIteration(accumulator, blockId, blockInfo, epochNumber)(op)
  }
}

object ConsensusDataProvider {
  def calculateNonceForGenesisBlockInfo(genesisBlockInfo: SidechainBlockInfo): NonceConsensusEpochInfo = {
    NonceConsensusEpochInfo(ConsensusNonce(Utils.doubleSHA256Hash(genesisBlockInfo.vrfProofHash.bytes())))
  }
}

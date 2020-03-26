package com.horizen.consensus

import java.math.BigInteger

import com.horizen.chain.SidechainBlockInfo
import com.horizen.params.NetworkParamsUtils
import com.horizen.storage.SidechainBlockInfoProvider
import scorex.util.{ModifierId, ScorexLogging}

import scala.annotation.tailrec

trait ConsensusDataProvider {
  this: TimeToEpochSlotConverter
    with NetworkParamsUtils
    with ScorexLogging {
    val storage: SidechainBlockInfoProvider
    val consensusDataStorage: ConsensusDataStorage
  } =>

  def getFullConsensusEpochInfoForBlock(blockId: ModifierId, blockInfo: SidechainBlockInfo): FullConsensusEpochInfo = {
    log.debug(s"Requested FullConsensusEpochInfo for ${blockId} block id")

    val previousEpochId: ConsensusEpochId = getPreviousConsensusEpochIdForBlock(blockId, blockInfo)

    val nonceEpochInfo: NonceConsensusEpochInfo = getNonceConsensusEpochAndUpdateIfNecessary(previousEpochId)

    val lastBlockIdInPreviousEpoch = lastBlockIdInEpochId(previousEpochId)
    val prePreviousEpochId: ConsensusEpochId = getPreviousConsensusEpochIdForBlock(lastBlockIdInPreviousEpoch, storage.blockInfoById(lastBlockIdInPreviousEpoch))
    val stakeEpochInfo: StakeConsensusEpochInfo =
      consensusDataStorage
        .getStakeConsensusEpochInfo(prePreviousEpochId)
        .getOrElse(throw new IllegalStateException(s"Stake was not defined for epoch ${prePreviousEpochId}"))

    FullConsensusEpochInfo(stakeEpochInfo, nonceEpochInfo)
  }

  private def getNonceConsensusEpochAndUpdateIfNecessary(epochId: ConsensusEpochId): NonceConsensusEpochInfo = {
    consensusDataStorage.getNonceConsensusEpochInfo(epochId).getOrElse{
      val newNonceInfo: NonceConsensusEpochInfo = calculateNonceForEpoch(epochId)
      consensusDataStorage.addNonceConsensusEpochInfo(epochId, newNonceInfo)
      newNonceInfo
    }
  }

  private def calculateNonceForEpoch(epochId: ConsensusEpochId): NonceConsensusEpochInfo = {
    val lastBlockIdInEpoch: ModifierId = lastBlockIdInEpochId(epochId)
    val lastBlockInfoInEpoch: SidechainBlockInfo = storage.blockInfoById(lastBlockIdInEpoch)

    val nonceOpt: Option[BigInteger] = foldEpochRight[Option[BigInteger]](None, lastBlockIdInEpoch, lastBlockInfoInEpoch){
      (blockId: ModifierId, blockInfo: SidechainBlockInfo, accumulator: Option[BigInteger]) =>
        {
          val minimalHashForCurrentBlockOpt: Option[BigInteger] = getMinimalHashOpt(blockInfo.mainchainReferenceDataHeaderHashes.map(_.data))
          (minimalHashForCurrentBlockOpt, accumulator) match {
            case (None, _) => accumulator
            case (minimalHashOpt, None) => minimalHashOpt
            case (Some(a), Some(b)) => Option(a.min(b))
        }
      }
    }

    assert(nonceOpt.isDefined, "No mainchain reference had been found for whole consensus epoch") //crash whole world here?

    NonceConsensusEpochInfo(bigIntToConsensusNonce(nonceOpt.get))
  }

  /**
   * @return Return last block in previous epoch, for genesis block last block of previous epoch is genesis block itself
   */
  def getPreviousConsensusEpochIdForBlock(blockId: ModifierId, blockInfo: SidechainBlockInfo): ConsensusEpochId = {
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

package com.horizen

import com.horizen.block.{MainchainBlockReference, MainchainHeader, SidechainBlock, SidechainBlockBase}
import com.horizen.chain.{FeePaymentsInfo, MainchainBlockReferenceDataInfo, MainchainHeaderBaseInfo, MainchainHeaderHash, MainchainHeaderInfo, SidechainBlockInfo, byteArrayToMainchainHeaderHash}
import com.horizen.consensus.{ConsensusDataProvider, ConsensusDataStorage}
import com.horizen.node.{NodeHistory, NodeHistoryBase}
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.storage.leveldb.Algos.encoder
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.validation.{SemanticBlockValidator, SemanticBlockValidatorBase}
import scorex.core.consensus.History.{Equal, Fork, ModifierIds, Nonsense, Older, ProgressInfo, Younger}
import scorex.core.consensus.{History, ModifierSemanticValidity}
import scorex.core.transaction.Transaction
import scorex.core.validation.RecoverableModifierError
import scorex.util.{ModifierId, ScorexLogging, idToBytes}
import com.horizen.node.util.MainchainBlockReferenceInfo

import java.util.Optional
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.{Failure, Success, Try}


abstract class AbstractHistory[TX <: Transaction, PM <: SidechainBlockBase[TX], HT <: AbstractHistory[TX, PM, HT]](
      val storage: SidechainHistoryStorage,
      val consensusDataStorage: ConsensusDataStorage,
      val params: NetworkParams,
      semanticBlockValidators: Seq[SemanticBlockValidatorBase[TX]]
  ) extends scorex.core.consensus.History[PM, SidechainSyncInfo, HT]
  with NetworkParamsUtils
  with ConsensusDataProvider
  with NodeHistoryBase
  with ScorexLogging
{

  def height: Int = storage.height
  def bestBlockId: ModifierId = storage.bestBlockId


  override def isEmpty: Boolean = height <= 0

  override def contains(id: ModifierId): Boolean = storage.blockInfoOptionById(id).isDefined

  override def applicableTry(block: PM): Try[Unit] = {
    if (!contains(block.parentId))
      Failure(new RecoverableModifierError("Parent block is not in history yet"))
    else
      Success(Unit)
  }

  def blockIdByHeight(height: Int): Option[String] = {
    storage.activeChainBlockId(height)
  }

  override def isSemanticallyValid(blockId: ModifierId): ModifierSemanticValidity = {
    storage.semanticValidity(blockId)
  }

  override def openSurfaceIds(): Seq[ModifierId] = {
    if(isEmpty)
      Seq()
    else
      Seq(bestBlockId)
  }


  override def continuationIds(info: SidechainSyncInfo, size: Int): ModifierIds = {
    info.knownBlockIds.find(id => storage.isInActiveChain(id)) match {
      case Some(commonBlockId) =>
        storage.activeChainAfter(commonBlockId).tail.take(size).map(id => (SidechainBlockBase.ModifierTypeId, id))
      case None =>
        //log.warn("Found chain without common block ids from remote")
        Seq()
    }
  }

  // see https://en.bitcoin.it/wiki/Protocol_documentation#getblocks
  protected def knownBlocksHeightToSync(): Seq[Int] = {
    if (isEmpty)
      return Seq()

    var indexes: Seq[Int] = Seq()

    var step: Int = 1
    // Start at the top of the chain and work backwards.
    var index: Int = height
    while (index > 1) {
      indexes = indexes :+ index
      // Push top 10 indexes first, then back off exponentially.
      if(indexes.size >= 10)
        step *= 2
      index -= step
    }
    // Push the genesis block index.
    indexes :+ 1
  }

  override def syncInfo: SidechainSyncInfo = {
    // collect control points of block ids like in bitcoin (last 10, then increase step exponentially until genesis block)
    SidechainSyncInfo(
      // return a sequence of block ids for given blocks height backward starting from blockId
      knownBlocksHeightToSync().map(height => storage.activeChainBlockId(height).get)
    )

  }

  // get divergent suffix until we reach the end of otherBlockIds or known block in otherBlockIds.
  // return last common block + divergent suffix
  // Note: otherBlockIds ordered from most recent to oldest block
  private def divergentSuffix(otherBlockIds: Seq[ModifierId]): Seq[ModifierId] = {
    var suffix = Seq[ModifierId]()
    var blockId: ModifierId = null
    var restOfOtherBlockIds = otherBlockIds

    while(restOfOtherBlockIds.nonEmpty) {
      blockId = restOfOtherBlockIds.head
      restOfOtherBlockIds = restOfOtherBlockIds.tail
      suffix = blockId +: suffix

      if(storage.isInActiveChain(blockId))
        return suffix
    }
    Seq() // we didn't find common block (even genesis one is different) -> we have totally different chains.
  }

  /** From Scorex History:
   * Whether another's node syncinfo shows that another node is ahead or behind ours
   *
   * @param other other's node sync info
   * @return Equal if nodes have the same history, Younger if another node is behind, Older if a new node is ahead
   */
  override def compare(other: SidechainSyncInfo): History.HistoryComparisonResult = {
    val dSuffix = divergentSuffix(other.knownBlockIds)

    dSuffix.size match {
      case 0 =>
        // log.warn(Nonsence situation..._)
        Nonsense
      case 1 =>
        if(dSuffix.head.equals(bestBlockId))
          Equal
        else
          Younger
      case _ =>
        val otherBestKnownBlockIndex = dSuffix.size - 1 - dSuffix.reverse.indexWhere(id => storage.heightOf(id).isDefined)
        val otherBestKnownBlockHeight = storage.heightOf(dSuffix(otherBestKnownBlockIndex)).get
        // other node height can be approximatly calculated as height of other KNOWN best block height + size of rest unknown blocks after it.
        // why approximately? see knownBlocksHeightToSync algorithm: blocks to sync step increasing.
        // to do: need to discuss
        val otherBestBlockApproxHeight = otherBestKnownBlockHeight + (dSuffix.size - 1 - otherBestKnownBlockIndex)
        if (storage.height < otherBestBlockApproxHeight)
          Older
        else if (storage.height == otherBestBlockApproxHeight) {
          if(otherBestBlockApproxHeight == otherBestKnownBlockHeight)
            Equal // UPDATE: FORK in both cases
          else
            Fork
        }
        else
          Younger
    }
  }

  override def getLastBlockIds(count: Int): java.util.List[String] = {
    val blockList = new java.util.ArrayList[String]()
    if(isEmpty)
      blockList
    else {
      var id = bestBlockId
      blockList.add(id)
      while (blockList.size < count && !isGenesisBlock(id)) {
        val parentBlockId = storage.parentBlockId(id).get
        blockList.add(parentBlockId)
        id = parentBlockId
      }
      blockList
    }
  }

  override def getBlockIdByHeight(height: Int): java.util.Optional[String] = {
    storage.activeChainBlockId(height) match {
      case Some(blockId) => java.util.Optional.of[String](blockId)
      case None => java.util.Optional.empty()
    }
  }

  override def getBlockHeightById(id: String): java.util.Optional[Integer] = {
    storage.heightOf(ModifierId(id)) match {
      case Some(height) => java.util.Optional.of[Integer](height)
      case None => java.util.Optional.empty()
    }
  }


  override def getCurrentHeight: Int = {
    height
  }

  override def getBlockHeight(blockId: String): java.util.Optional[Integer] = {
    storage.blockInfoOptionById(ModifierId(blockId)).map(info => Integer.valueOf(info.height)).asJava
  }

  def feePaymentsInfo(blockId: ModifierId): Option[FeePaymentsInfo] = {
    storage.getFeePaymentsInfo(blockId)
  }

  /*
  All the methods in SidechainHistory and NodeHistory, that work with MainchainBlockReferences,
  MainchainHeaders, MainchainBlockReferenceData itself or any information about them,
  are designed to work with ActiveChain data only.
  In a Sidechain we are not interested in Mainchain data form Sidechain forks.
 */
  override def getMainchainCreationBlockHeight: Int = params.mainchainCreationBlockHeight

  override def getBestMainchainBlockReferenceInfo: java.util.Optional[MainchainBlockReferenceInfo] = {
    storage.getBestMainchainBlockReferenceInfo.asJava
  }

  override def getMainchainBlockReferenceInfoByMainchainBlockHeight(height: Int): java.util.Optional[MainchainBlockReferenceInfo] = {
    storage.getMainchainBlockReferenceInfoByMainchainBlockHeight(height).asJava
  }

  override def getMainchainBlockReferenceInfoByHash(mainchainBlockReferenceHash: Array[Byte]): java.util.Optional[MainchainBlockReferenceInfo] = {
    storage.getMainchainBlockReferenceInfoByHash(mainchainBlockReferenceHash).asJava
  }

  override def getMainchainBlockReferenceByHash(mainchainHeaderHash: Array[Byte]): Optional[MainchainBlockReference] = {
    storage.getMainchainBlockReferenceByHash(mainchainHeaderHash).asJava
  }

  override def getMainchainHeaderByHash(mainchainHeaderHash: Array[Byte]): Optional[MainchainHeader] = {
    storage.getMainchainHeaderByHash(mainchainHeaderHash).asJava
  }

  override def getMainchainHeaderInfoByHash(mainchainHeaderHash: Array[Byte]): java.util.Optional[MainchainHeaderInfo] = {
    mainchainHeaderInfoByHash(mainchainHeaderHash).asJava
  }

  def getBestMainchainHeaderInfo: Option[MainchainHeaderInfo] = storage.getBestMainchainHeaderInfo

  def getMainchainHeaderInfoByHeight(height: Int): Option[MainchainHeaderInfo] = storage.getMainchainHeaderInfoByHeight(height)

  def mainchainHeaderInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainHeaderInfo] = storage.getMainchainHeaderInfoByHash(mainchainHeaderHash)

  def getBestMainchainBlockReferenceDataInfo: Option[MainchainBlockReferenceDataInfo] = storage.getBestMainchainBlockReferenceDataInfo

  def getMainchainBlockReferenceDataInfoByHeight(height: Int): Option[MainchainBlockReferenceDataInfo] = storage.getMainchainBlockReferenceDataInfoByHeight(height)

  def getMainchainBlockReferenceDataInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceDataInfo] = storage.getMainchainBlockReferenceDataInfoByHash(mainchainHeaderHash)

  // Create MC Locator sequence from most recent Mainchain Header to MC Creation Block
  // Locator in Bitcoin style
  def getMainchainHashesLocator: Seq[MainchainHeaderHash] = {
    val firstMainchainHeaderHeight: Int = getMainchainCreationBlockHeight

    val indexes: ListBuffer[Int] = ListBuffer()
    var step: Int = 1
    var index: Int = storage.getBestMainchainHeaderInfo.get.height

    while (index > firstMainchainHeaderHeight) {
      indexes.append(index)
      // Push top 10 indexes first, then back off exponentially.
      if (indexes.size >= 10)
        step *= 2
      index -= step
    }
    // Push the genesis mc ref index.
    indexes.append(firstMainchainHeaderHeight)

    storage.getMainchainHashesForIndexes(indexes)
  }

  // Retrieve the sequence of MainchainHeader hashes from last hash to best hash.
  // best - the most recent hash, last - the oldest hash
  def getMainchainHashes(last: Array[Byte], best: Array[Byte]): Seq[MainchainHeaderHash] = {
    val bestHeight = storage.getMainchainHeaderInfoByHash(best)
      .getOrElse(throw new IllegalArgumentException(s"MainchainHeader hash $best not found."))
      .height
    val lastHeight = storage.getMainchainHeaderInfoByHash(last)
      .getOrElse(throw new IllegalArgumentException(s"MainchainHeader hash $last not found."))
      .height
    require(bestHeight >= lastHeight, "Best hash must lead to the higher MainchainHeader than the last one.")

    val indexes: Seq[Int] = lastHeight to bestHeight
    storage.getMainchainHashesForIndexes(indexes)
  }

  // Return Hashes of MainchainHeader that are present in Active chain, but wait for MainchainBlockReferenceData to be synchronized.
  // Hashes ordered from oldest to MC tip
  def missedMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = {
    val bestMainchainReferenceDataHeight: Int = getBestMainchainBlockReferenceDataInfo.get.height
    val bestMainchainHeaderHeight: Int = getBestMainchainHeaderInfo.get.height

    if(bestMainchainHeaderHeight == bestMainchainReferenceDataHeight)
      Seq()
    else {
      val missedDataIndexes = bestMainchainReferenceDataHeight + 1 to bestMainchainHeaderHeight
      storage.getMainchainHashesForIndexes(missedDataIndexes)
    }
  }

  
  // Go back though chain and get block ids until condition 'until' or reaching the limit
  // None if parent block is not in chain
  // Note: work faster for active chain back (looks inside memory) and slower for fork chain (looks inside disk)
  private def chainBack(blockId: ModifierId,
                        until: ModifierId => Boolean,
                        limit: Int): Option[Seq[ModifierId]] = { // to do
    var acc: Seq[ModifierId] = Seq(blockId)
    var id = blockId

    while(acc.size < limit && !until(id)) {
      storage.parentBlockId(id) match {
        case Some(parentId) =>
          acc = parentId +: acc
          id = parentId
        case _ =>
          //log.warn(s"Parent block for ${encoder.encode(block.id)} not found ")
          return None
      }
    }
    Some(acc)
  }

  // Find common suffixes for two chains - starting from forkBlock and from bestBlock.
  // Returns last common block and then variant blocks for two chains.
  protected def commonBlockSuffixes(forkBlock: PM): (Seq[ModifierId], Seq[ModifierId]) = {
    chainBack(forkBlock.id, storage.isInActiveChain, Int.MaxValue) match {
      case Some(newBestChain) =>
        val commonBlockHeight = storage.blockInfoById(newBestChain.head).height
        if(height - commonBlockHeight > params.maxHistoryRewritingLength)
        // fork length is more than params.maxHistoryRewritingLength
          (Seq[ModifierId](), Seq[ModifierId]())
        else
          (newBestChain, storage.activeChainAfter(newBestChain.head))

      case None => (Seq[ModifierId](), Seq[ModifierId]())
    }
  } ensuring { res =>
    // verify, that both sequences starts from common block
    (res._1.isEmpty && res._2.isEmpty) || res._1.head == res._2.head
  }

  def bestForkChanges(block: PM): Try[ProgressInfo[PM]]

  def isBestBlock(block: PM, parentBlockInfo: SidechainBlockInfo): Boolean = {
    val currentScore = storage.chainScoreFor(bestBlockId).get
    val newScore = calculateChainScore(block, parentBlockInfo.score)
    if (newScore == currentScore) {
      (parentBlockInfo.height + 1) > height
    } else {
      newScore > currentScore
    }
  }

  def calculateGenesisBlockInfo(block: PM, params: NetworkParams): SidechainBlockInfo = {
    require(block.id == params.sidechainGenesisBlockId, "Passed block is not a genesis block.")

    SidechainBlockInfo(
      1,
      block.score,
      block.parentId,
      block.timestamp,
      ModifierSemanticValidity.Unknown,
      // First MC header Cumulative CommTree hash is provided by genesis info
      Seq(MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(block.mainchainHeaders.head.hash), params.initialCumulativeCommTreeHash)),
      SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock[TX](block),
      // TODO check this
      WithdrawalEpochUtils.getWithdrawalEpochInfo[TX](block, WithdrawalEpochInfo(0,0), params),
      None,
      block.id,
    )
  }

  def calculateChainScore(block: PM, parentScore: Long): Long = {
    parentScore + block.score
  }

  protected def calculateBlockInfo(block: PM, parentBlockInfo: SidechainBlockInfo): SidechainBlockInfo = {
    val lastBlockInPreviousConsensusEpoch = getLastBlockInPreviousConsensusEpoch(block.timestamp, block.parentId)
    val nonceConsensusEpochInfo = getOrCalculateNonceConsensusEpochInfo(block.header.timestamp, block.header.parentId)
    val vrfOutputOpt = getVrfOutput(block.header, nonceConsensusEpochInfo)
    val blockMainchainHeaderBaseInfoSeq: Seq[MainchainHeaderBaseInfo] = if(block.mainchainHeaders.isEmpty) Seq() else {
      val prevBaseInfo:MainchainHeaderBaseInfo = storage.getLastMainchainHeaderBaseInfoInclusion(block.parentId)
      MainchainHeaderBaseInfo.getMainchainHeaderBaseInfoSeqFromBlock(block, prevBaseInfo.cumulativeCommTreeHash)
    }

    SidechainBlockInfo(
      parentBlockInfo.height + 1,
      calculateChainScore(block, parentBlockInfo.score),
      block.parentId,
      block.timestamp,
      ModifierSemanticValidity.Unknown,
      blockMainchainHeaderBaseInfoSeq,
      SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock(block),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, parentBlockInfo.withdrawalEpochInfo, params),
      vrfOutputOpt, //technically block is not correct from consensus point of view if vrfOutput is None
      lastBlockInPreviousConsensusEpoch
    )
  }

  // TODO WORK IN PROGRESS
  override def append(block: PM): Try[(HT, ProgressInfo[PM])] = {


    for(validator <- semanticBlockValidators)
      validator.validate(block).get

    // Non-genesis blocks mast have a parent already present in History
    val parentBlockInfoOption: Option[SidechainBlockInfo] = storage.blockInfoOptionById(block.parentId)
    if(!isGenesisBlock(block.id) && parentBlockInfoOption.isEmpty)
      throw new IllegalArgumentException("Sidechain block %s appending failed: parent block is missed.".format(BytesUtils.toHexString(idToBytes(block.id))))

    // TODO
//    for(validator <- historyBlockValidators)
//      validator.validate(block, this).get

    val (newStorage: Try[SidechainHistoryStorage], progressInfo: ProgressInfo[PM]) = {
      if(isGenesisBlock(block.id)) {
        (
          storage.update(block, calculateGenesisBlockInfo(block, params)),
          ProgressInfo(None, Seq(), Seq(block), Seq())
        )
      }
      else {
        val parentBlockInfo = parentBlockInfoOption.get
        val blockInfo: SidechainBlockInfo = calculateBlockInfo(block, parentBlockInfo)
        // Check if we retrieved the next block of best chain
        if (block.parentId.equals(bestBlockId)) {
          (
            storage.update(block, blockInfo),
            ProgressInfo(None, Seq(), Seq(block), Seq())
          )
        } else {
          // Check if retrieved block is the best one, but from another chain
          if (isBestBlock(block, parentBlockInfo)) {
            bestForkChanges(block) match { // get info to switch to another chain
              case Success(progInfo) =>
                (
                  storage.update(block, blockInfo),
                  progInfo
                )
              case Failure(e) =>
                //log.error("New best block found, but it can not be applied: %s".format(e.getMessage))
                (
                  storage.update(block, blockInfo),
                  // TO DO: we should somehow prevent growing of such chain (penalize the peer?)
                  ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
                )

            }
          } else {
            // We retrieved block from another chain that is not the best one
            (
              storage.update(block, blockInfo),
              ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
            )
          }
        }
      }
    }
    // TODO
      // new SidechainHistory(newStorage.get, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators) -> progressInfo


    // just for test, remove it
    throw new IllegalArgumentException("parent block %s.".format(BytesUtils.toHexString(idToBytes(block.parentId))))
  }
}

object AbstractHistory {
  def calculateGenesisBlockInfo[TX <: Transaction](block: SidechainBlockBase[TX], params: NetworkParams): SidechainBlockInfo = {
    require(block.id == params.sidechainGenesisBlockId, "Passed block is not a genesis block.")

    SidechainBlockInfo(
      1,
      block.score,
      block.parentId,
      block.timestamp,
      ModifierSemanticValidity.Unknown,
      // First MC header Cumulative CommTree hash is provided by genesis info
      Seq(MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(block.mainchainHeaders.head.hash), params.initialCumulativeCommTreeHash)),
      SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock[TX](block),
      // TODO check this
      WithdrawalEpochUtils.getWithdrawalEpochInfo[TX](block, WithdrawalEpochInfo(0,0), params),
      None,
      block.id,
    )
  }
}
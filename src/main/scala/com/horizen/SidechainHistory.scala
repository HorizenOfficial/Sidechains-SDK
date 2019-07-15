package com.horizen

import com.horizen.block.{ProofOfWorkVerifier, SidechainBlock}
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.BytesUtils
import scorex.core.NodeViewModifier
import scorex.util.ModifierId
import scorex.core.consensus.History._
import scorex.core.consensus.{History, ModifierSemanticValidity}
import scorex.core.validation.RecoverableModifierError
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}


class SidechainHistory(val storage: SidechainHistoryStorage, params: NetworkParams)
  extends scorex.core.consensus.History[
      SidechainBlock,
      SidechainSyncInfo,
      SidechainHistory] with scorex.core.utils.ScorexEncoding {

  override type NVCT = SidechainHistory

  require(NodeViewModifier.ModifierIdSize == 32, "32 bytes ids assumed")

  val height: Long = storage.height
  val bestBlockId: ModifierId = storage.bestBlockId
  val bestBlock: SidechainBlock = storage.bestBlock // note: maybe make it lazy?


  // Note: if block already exists in History it will be declined inside NodeViewHolder before appending.
  override def append(block: SidechainBlock): Try[(SidechainHistory, ProgressInfo[SidechainBlock])] = Try {
    // TO DO: validate using Validators.
    // TO DO: validate against Praos consensus rules.
    if(!block.semanticValidity(params))
      throw new IllegalArgumentException("Semantic validation failed for block %s".format(BytesUtils.toHexString(idToBytes(block.id))))
    if(!ProofOfWorkVerifier.checkNextWorkRequired(block, this, params))
      throw new IllegalArgumentException("Containing MC Blocks PoW difficulty is invalid for block %s".format(BytesUtils.toHexString(idToBytes(block.id))))

    val (newStorage: Try[SidechainHistoryStorage], progressInfo: ProgressInfo[SidechainBlock]) = {
      if(isGenesisBlock(block.id)) {
        (
          storage.update(block, 1L, isBest = true),
          ProgressInfo(None, Seq(), Seq(block), Seq())
        )
      }
      else {
        storage.heightOf(block.parentId) match {
          case Some(parentHeight) =>
            val chainScore: Long = calculateChainScore(block, parentHeight)
            // Check if we retrieved the next block of best chain
            if(block.parentId.equals(bestBlockId)) {
              (
                storage.update(block, chainScore, isBest = true),
                ProgressInfo(None, Seq(), Seq(block), Seq())
              )
            } else {
              // Check if retrieved block is the best one, but from another chain
              if(isBestBlock(block, parentHeight)) {
                (
                  storage.update(block, chainScore, isBest = true),
                  bestForkChanges(block) // get info to switch to another chain
                )
              } else {
                // We retrieved block from another chain that is not the best one
                (
                  storage,
                  ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
                )
              }
            }
          case None =>
            // Parent is not present inside history
            // TO DO: Request for common chain till current unknown block. Check Scorex RC4 for possible solution
            // TO DO: do we need to save it to history storage to prevent double downloading ot it's cached somewhere?
            (
              storage,
              ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
            )
        }
      }
    }
    new SidechainHistory(newStorage.get, params) -> progressInfo
  }

  def isGenesisBlock(blockId: ModifierId): Boolean = {
    blockId.equals(params.sidechainGenesisBlockId)
  }

  def isBestBlock(block: SidechainBlock, parentHeight: Long): Boolean = {
    val currentScore = storage.chainScoreFor(bestBlockId).get
    val newScore = calculateChainScore(block, parentHeight)
    newScore > currentScore
  }

  // TO DO: define algorithm for Block score computation
  def calculateChainScore(block: SidechainBlock, parentHeight: Long): Long = {
    storage.chainScoreFor(block.parentId).get + 1L
  }

  // Note: do we need to check validity of newChainSuffix blocks like in HybridApp?
  def bestForkChanges(block: SidechainBlock): ProgressInfo[SidechainBlock] = {
    val (newChainSuffix, currentChainSuffix) = commonBlockSuffixes(modifierById(block.parentId).get)

    val newChainSuffixValidity: Boolean = !newChainSuffix.drop(1).map(storage.semanticValidity)
      .contains(ModifierSemanticValidity.Invalid)

    if(newChainSuffixValidity) {
      val rollbackPoint = newChainSuffix.headOption
      val toRemove = currentChainSuffix.tail.map(id => storage.blockById(id).get)
      val toApply = newChainSuffix.tail.map(id => storage.blockById(id).get) ++ Seq(block)

      require(toRemove.nonEmpty)
      require(toApply.nonEmpty)

      ProgressInfo[SidechainBlock](rollbackPoint, toRemove, toApply, Seq())
    } else {
      //log.info(s"Orphaned block $block from invalid suffix")
      ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
    }
  }

  // Find common suffixes for two chains - starting from forkBlock and from bestBlock.
  // Returns last common block and then variant blocks for two chains.
  def commonBlockSuffixes(forkBlock: SidechainBlock): (Seq[ModifierId], Seq[ModifierId]) = {
    val currentChain = chainBack(bestBlockId, isGenesisBlock, params.maxHistoryRewritingLength).get

    def inCurrentChain(blockId: ModifierId): Boolean = currentChain.contains(blockId)
    val newBestChain = chainBack(forkBlock.id, inCurrentChain, params.maxHistoryRewritingLength).get

    val i = currentChain.indexWhere { id =>
      newBestChain.headOption match {
        case None => false
        case Some(newBestChainHead) => id == newBestChainHead
      }
    }
    (newBestChain, currentChain.takeRight(currentChain.length - i))
  } ensuring { res =>
    // verify, that both sequences starts from common block
    res._1.head == res._2.head
  }

  // Go back though chain and get block ids until condition 'until' or reaching the limit
  // None if parent block is not in chain
  // TO DO: look up through block ids without parsing the whole block (store parent id separately or parse only first 32 bytes of SC block in ScHisStorage)
  private def chainBack(blockId: ModifierId,
                        until: ModifierId => Boolean,
                        limit: Int,
                        acc: Seq[ModifierId] = Seq()): Option[Seq[ModifierId]] = {
    val sum: Seq[ ModifierId] = blockId +: acc

    if (limit <= 0 || until(blockId)) {
      Some(sum)
    } else {
      storage.parentBlockId(blockId) match {
        case Some(parentId) => chainBack(parentId, until, limit - 1, sum)
        case _ =>
          //log.warn(s"Parent block for ${encoder.encode(block.id)} not found ")
          None
      }
    }
  }

  override def reportModifierIsValid(block: SidechainBlock): SidechainHistory = {
    Try {
      var newStorage = storage.updateSemanticValidity(block, ModifierSemanticValidity.Valid).get
      newStorage = newStorage.updateBestBlock(block).get
      new SidechainHistory(newStorage, params)
    } match {
      case Success(newHistory) => newHistory
      case Failure(e) => {
        //log.error(s"Failed to update validity for block ${encoder.encode(block.id)} with error ${e.getMessage}.")
        new SidechainHistory(storage, params)
      }
    }
  }

  override def reportModifierIsInvalid(modifier: SidechainBlock, progressInfo: History.ProgressInfo[SidechainBlock]): (SidechainHistory, History.ProgressInfo[SidechainBlock]) = {
    val newHistory: SidechainHistory = Try {
      val newStorage = storage.updateSemanticValidity(modifier, ModifierSemanticValidity.Invalid).get
      new SidechainHistory(newStorage, params)
    } match {
      case Success(history) => history
      case Failure(e) =>
        //log.error(s"Failed to update validity for block ${encoder.encode(block.id)} with error ${e.getMessage}.")
        new SidechainHistory(storage, params)
    }
    newHistory -> ProgressInfo(None, Seq(), Seq(), Seq())
  }

  override def isEmpty: Boolean = height <= 0

  override def applicableTry(block: SidechainBlock): Try[Unit] = {
    if (!contains(block.parentId))
      Failure(new RecoverableModifierError("Parent block is not in history yet"))
    else
      Success()
  }

  override def modifierById(blockId: ModifierId): Option[SidechainBlock] = storage.blockById(blockId)

  override def isSemanticallyValid(blockId: ModifierId): ModifierSemanticValidity = {
    modifierById(blockId) match {
      case Some(block) =>
        if(block.semanticValidity(params))
          ModifierSemanticValidity.Valid
        else
          ModifierSemanticValidity.Invalid
      case None => ModifierSemanticValidity.Absent
    }
  }

  override def openSurfaceIds(): Seq[ModifierId] = {
    if(isEmpty)
      Seq()
    else
      Seq(bestBlockId)
  }

  override def continuationIds(info: SidechainSyncInfo, size: Int): Option[ModifierIds] = {
    def inInfoKnownBlocks(id: ModifierId): Boolean = info.knownBlockIds.contains(id) || isGenesisBlock(id)

    chainBack(bestBlockId, inInfoKnownBlocks, Int.MaxValue) match {
      case Some(chain) =>
        if(chain.exists(id => info.knownBlockIds.contains(id)))
          Some(chain.take(size).map(id => (SidechainBlock.ModifierTypeId, id)))
        else {
          //log.warn("Found chain without ids from remote")
          None
        }
      case _ => None
    }
  }

  // TO DO: return seq like in Bitcoin with increasing step between block, until genesis is reached.
  private def lastKnownBlocks(count: Int): Seq[ModifierId] = {
    if(isEmpty)
      Seq()
    else {
      chainBack(bestBlockId, isGenesisBlock, count) match {
        case Some(seq) => seq
        case None => Seq()
      }
    }
  }

  override def syncInfo: SidechainSyncInfo = {
    SidechainSyncInfo(
      lastKnownBlocks(params.maxHistoryRewritingLength)
    )

  }

  // get divergent suffix until we reach the end of otherBlocks or known block in otherBlocks.
  // return last common block + divergent suffix.
  // Note: otherKnownBlockIds ordered from most recent to oldest block
  private def divergentSuffix(otherKnownBlockIds: Seq[ModifierId],
                              suffixFound: Seq[ModifierId] = Seq()): Seq[ModifierId] = {
    if(otherKnownBlockIds.isEmpty)
      Seq()
    else {
      val head = otherKnownBlockIds.head
      val newSuffix = suffixFound :+ head
      modifierById(head) match {
        case Some(_) =>
          newSuffix
        case None => if (otherKnownBlockIds.length <= 1) {
          Seq()
        } else {
          val otherKnownBlockIdsTail = otherKnownBlockIds.tail
          divergentSuffix(otherKnownBlockIdsTail, newSuffix)
        }
      }
    }
  }

  /** From Scorex History:
    * Whether another's node syncinfo shows that another node is ahead or behind ours
    *
    * @param other other's node sync info
    * @return Equal if nodes have the same history, Younger if another node is behind, Older if a new node is ahead
    */
  override def compare(other: SidechainSyncInfo): History.HistoryComparisonResult = {
    val dSuffix = divergentSuffix(other.knownBlockIds.reverse)

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
        val localSuffixLength = storage.heightOf(bestBlockId).get - storage.heightOf(dSuffix.last).get
        val otherSuffixLength = dSuffix.length
        if (localSuffixLength < otherSuffixLength)
          Older
        else if (localSuffixLength == otherSuffixLength)
          Equal // TO DO: should be a fork? Look for RC4
        else
          Younger
    }
  }
}

package com.horizen

import java.util
import java.util.Optional

import com.horizen.block.SidechainBlock
import scorex.util.{ModifierId, bytesToId, idToBytes}
import scorex.core.consensus.History.ModifierIds
import scorex.core.consensus.{History, ModifierSemanticValidity, SyncInfo}

import scala.util.Try
import com.horizen.node.NodeHistory
import com.horizen.storage.SidechainHistoryStorage

import scala.collection.JavaConverters

// TO DO: implement it like in HybridHistory
// TO DO: think about additional methods (consensus related?)


class SidechainHistory(val storage : SidechainHistoryStorage)
  extends scorex.core.consensus.History[
      SidechainBlock,
      SyncInfo,
      SidechainHistory] with NodeHistory {

  val height: Int = storage.height
  val bestBlockId: ModifierId = storage.bestBlockId
  val bestBlock: SidechainBlock = storage.bestBlock

  override type NVCT = SidechainHistory

  override def append(modifier: SidechainBlock): Try[(SidechainHistory, History.ProgressInfo[SidechainBlock])] = ???

  override def reportModifierIsValid(modifier: SidechainBlock): SidechainHistory = ???

  override def reportModifierIsInvalid(modifier: SidechainBlock, progressInfo: History.ProgressInfo[SidechainBlock]): (SidechainHistory, History.ProgressInfo[SidechainBlock]) = ???

  override def isEmpty: Boolean = ???

  override def applicableTry(modifier: SidechainBlock): Try[Unit] = ???

  override def modifierById(modifierId: ModifierId): Option[SidechainBlock] = ???

  override def isSemanticallyValid(modifierId: ModifierId): ModifierSemanticValidity = ???

  override def openSurfaceIds(): Seq[ModifierId] = ???

  override def continuationIds(info: SyncInfo, sie: Int): Option[ModifierIds] = ???

  override def syncInfo: SyncInfo = ???

  override def compare(other: SyncInfo): History.HistoryComparisonResult = ???

  def isGenesisBlock(blockId: ModifierId): Boolean = ???

  def isBestBlock(block: SidechainBlock, parentScore: Long): Boolean = ???

  def chainBack(block : SidechainBlock, until : ModifierId => Boolean, limit : Int) : Option[Seq[ModifierId]] = ???

  override def getBlockById(blockId: Array[Byte]): Optional[SidechainBlock] = {
    Optional.ofNullable(
      modifierById(
        bytesToId(blockId)).orNull
    )
  }

  /**
    * Get the id of the previous block from a given block id
    */
  private def previousBlockId(blockId : ModifierId) : Option[ModifierId] = {
    modifierById(blockId).getOrElse() match {
      case Some(block) => storage.parentBlockId(blockId)
      case _ => None
    }
  }

  override def getLastBlockids(startBlock: SidechainBlock, count: Int): util.List[Array[Byte]] = {
    var seqOfBlockIds = chainBack(startBlock, isGenesisBlock, count).get
    if (count >= height){
      seqOfBlockIds = previousBlockId(seqOfBlockIds.head).get +: seqOfBlockIds
    }
    else{
      seqOfBlockIds = seqOfBlockIds.slice(seqOfBlockIds.length - count, seqOfBlockIds.length + 1)
    }
    JavaConverters.seqAsJavaList(seqOfBlockIds.map(id => idToBytes(id)))
  }

  override def getBestBlock(): SidechainBlock = bestBlock

  override def getBlockIdByHeight(height: Int): Array[Byte] = {
    var seqOfBlockIds = chainBack(bestBlock, isGenesisBlock, height).get
    var blockId : ModifierId =
      if(height == 1){
        var headBlockId = seqOfBlockIds.headOption.get
        previousBlockId(headBlockId).get
      }
      else
        seqOfBlockIds(height -2)

    idToBytes(blockId)
  }

  override def getCurrentHeight(): Int = height
}

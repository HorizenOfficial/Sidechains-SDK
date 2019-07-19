package com.horizen.chain

import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.ModifierId

import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.util.Try

class ActiveChain private (private var tipId: Option[ModifierId], private var blockInfos: ArrayBuffer[SidechainBlockInfo], private var blockHeightMap: HashMap[ModifierId, Int]) {

  def height(): Int = blockInfos.size

  def tip(): Option[ModifierId] = tipId

  def tipInfo(): SidechainBlockInfo = blockInfos.last

  def updateTip(newTipId: ModifierId, newTipInfo: SidechainBlockInfo): Try[Unit] = Try {
    if(height() > 0 && !newTipInfo.parentId.equals(tip())) {
      // we get a tip, that is a part of another chain
      heightOf(newTipInfo.parentId) match {
        case Some(parentHeight) =>
          // remove block info data for all blocks after new tip parent block
          for(i <- parentHeight + 1 until blockInfos.size)
            blockHeightMap.remove(blockInfos(i).parentId)
          blockHeightMap.remove(tipId.get)
          blockInfos = blockInfos.take(parentHeight)
        case None => throw new IllegalArgumentException("New tip's parentId is not a part of active chain. Failed to reorganize active chain.")
      }
    }

    // append to current chain
    tipId = Some(newTipId)
    blockHeightMap.put(newTipId, height() + 1)
    blockInfos.append(newTipInfo)
  }

  def updateSemanticValidity(id: ModifierId, status: ModifierSemanticValidity): Option[SidechainBlockInfo] = {
    blockHeightMap.get(id) match {
      case Some(height) =>
        val oldInfo = blockInfos(height - 1)
        blockInfos(height - 1) = SidechainBlockInfo(oldInfo.height, oldInfo.score, oldInfo.parentId, status)
        Some(blockInfos(height - 1))
      case None => None
    }
  }

  def heightOf(id: ModifierId): Option[Int] = blockHeightMap.get(id)

  def contains(id: ModifierId): Boolean = blockHeightMap.contains(id)

  def chainFrom(id: ModifierId): Seq[ModifierId] = {
    if(!contains(id))
      return Seq()
    var res: Seq[ModifierId] = Seq()

    var currentHeight = heightOf(id).get
    while(currentHeight < height()) {
      res = res :+ getBlockInfo(currentHeight).get.parentId
      currentHeight += 1
    }
    res :+ tip().get
  }

  def getBlockInfo(id: ModifierId): Option[SidechainBlockInfo] = {
    blockHeightMap.get(id) match {
      case Some(height) => Some(blockInfos(height - 1))
      case None => None
    }
  }

  def getBlockInfo(blockHeight: Int): Option[SidechainBlockInfo] = {
    if(blockHeight > height())
      None
    else
      Some(blockInfos(blockHeight - 1))
  }

  def getBlockId(blockHeight: Int): Option[ModifierId] = {
    if(blockHeight > height())
      None
    else if(blockHeight == height())
      tip()
    else
      Some(getBlockInfo(blockHeight + 1).get.parentId)
  }
}


object ActiveChain {
  // In case of empty storage
  def apply(): ActiveChain = {
    new ActiveChain(None, ArrayBuffer[SidechainBlockInfo](), HashMap[ModifierId, Int]())
  }

  // In case of storage with blocks
  def apply(blockInfosData: ArrayBuffer[(ModifierId, SidechainBlockInfo)]): ActiveChain = {
    new ActiveChain(
      Some(blockInfosData.last._1),
      blockInfosData.map(tuple => tuple._2),
      HashMap(blockInfosData.map(tuple => tuple._1 -> tuple._2.height): _*)
    )
  }
}
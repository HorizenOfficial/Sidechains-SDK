package com.horizen.chain

import com.horizen.utils.ByteArrayWrapper
import scorex.util.ModifierId

import scala.collection.mutable.ArrayBuffer

class ActiveChain private(private val sidechainCache: ChainedData[ModifierId, SidechainBlockInfo],
                          private val mainchainCache: ChainedData[ByteArrayWrapper, MainchainBlockReferenceData]) {
  // Sidechain data retrieval
  def height: Int = sidechainCache.height

  def bestId: Option[ModifierId] = sidechainCache.bestId

  def bestData: Option[SidechainBlockInfo] = sidechainCache.bestData

  def heightById(id: ModifierId): Option[Int] = sidechainCache.heightById(id)

  def contains(id: ModifierId): Boolean = sidechainCache.contains(id)

  def chainFrom(id: ModifierId): Seq[ModifierId] = sidechainCache.chainFrom(id)

  def dataById(id: ModifierId): Option[SidechainBlockInfo] = sidechainCache.heightById(id).flatMap(sidechainCache.dataByHeight)

  def dataByHeight(blockHeight: Int): Option[SidechainBlockInfo] = sidechainCache.dataByHeight(blockHeight)

  def idByHeight(blockHeight: Int): Option[ModifierId] = sidechainCache.idByHeight(blockHeight)

  // Mainchain data retrieval
  def mcHeightByMcId(mainChainReferenceId: ByteArrayWrapper): Option[Int] = mainchainCache.heightById(mainChainReferenceId)

  def mcIdByMcHeight(mcHeight: Int): Option[ByteArrayWrapper] = mainchainCache.idByHeight(mcHeight)

  def heightOfMc: Int = mainchainCache.height

  def dataOfMcByMcId (mainChainReferenceId: ByteArrayWrapper): Option[MainchainBlockReferenceData] = mainchainCache.dataById(mainChainReferenceId)

  // Mixed data retrieval
  def idByMcId(byteArrayWrapper: ByteArrayWrapper): Option[ModifierId] = {
    mainchainCache.dataById(byteArrayWrapper).flatMap(r => sidechainCache.idByHeight(r.sidechainHeight))
  }

  def heightByMcId(byteArrayWrapper: ByteArrayWrapper): Option[Int] = {
    mainchainCache.dataById(byteArrayWrapper).map(_.sidechainHeight)
  }

  // Add data
  def setBestBlock(newBestId: ModifierId, newBestData: SidechainBlockInfo, mainchainParent: Option[ByteArrayWrapper] = None): Unit = {
    // Every time when we have mainchain block references, appropriate parent shall be present as well
    require(newBestData.mainchainBlockReferenceHashes.isEmpty == mainchainParent.isEmpty,
      s"References are ${newBestData.mainchainBlockReferenceHashes.isEmpty} and parent ${ mainchainParent.isEmpty} inconsistency")

    if (height != 0 && !sidechainCache.contains(newBestData.parentId)) {
      throw new IllegalArgumentException(s"Try to add unconnected sidechain block with id ${newBestId} to an active chain")
    }

    cutBothStorages(newBestId, newBestData, mainchainParent)
    addToBothStorages(newBestId, newBestData, mainchainCache.bestId.orElse(mainchainParent))
  }

  private def getLastExistMainchainReferenceInSidechain: Option[ByteArrayWrapper] = {
    sidechainCache
      .getLastDataByPredicate(_.mainchainBlockReferenceHashes.nonEmpty)
      .map(_.mainchainBlockReferenceHashes.last)
  }

  private def cutBothStorages(newTipId: ModifierId, newTipInfo: SidechainBlockInfo, mainchainParent: Option[ByteArrayWrapper]): Unit = {
    sidechainCache.cutToId(newTipInfo.parentId)

    val mainchainCutPoint: Option[ByteArrayWrapper] = mainchainParent.orElse(getLastExistMainchainReferenceInSidechain)
    mainchainCutPoint match {
      case Some(parent) => mainchainCache.cutToId(parent)
      case None => mainchainCache.clear()
    }
  }

  private def addToBothStorages(newTipId: ModifierId, newTipInfo: SidechainBlockInfo, parentForMainchain: Option[ByteArrayWrapper]): Unit = {
    sidechainCache.appendData(newTipId, newTipInfo)

    val preparedMainchainReferences =
      buildMainchainBlockReferences(newTipInfo.mainchainBlockReferenceHashes, parentForMainchain)
    preparedMainchainReferences.foreach { case (id, data) => mainchainCache.appendData(id, data) }
  }

  private def buildMainchainBlockReferences(mainchainBlockHashes: Seq[ByteArrayWrapper], parentForMainchain: Option[ByteArrayWrapper]) = {
    if (mainchainBlockHashes.nonEmpty) {
      require(parentForMainchain.isDefined, "Parent is not defined for non empty mainchain references")
      val bufferForBuildingMainchainBlockReferences = (Seq[(ByteArrayWrapper, MainchainBlockReferenceData)](), parentForMainchain.get)
      mainchainBlockHashes.foldLeft(bufferForBuildingMainchainBlockReferences) {
        case ((data, parent), reference) => (data :+ (reference, MainchainBlockReferenceData(height, parent)), reference)
      }._1
    }
    else {
      Seq()
    }
  }
}

object ActiveChain {
  // In case of empty storage
  def apply(): ActiveChain = {
    new ActiveChain(new ChainedData[ModifierId, SidechainBlockInfo](), new ChainedData[ByteArrayWrapper, MainchainBlockReferenceData]())
  }

  // In case of storage with blocks
  def apply(blocksInfoData: ArrayBuffer[(ModifierId, SidechainBlockInfo)], mainchainParent: Option[ByteArrayWrapper]): ActiveChain = {
    require(blocksInfoData.headOption.flatMap(_._2.mainchainBlockReferenceHashes.headOption).isEmpty == mainchainParent.isEmpty)

    // @TODO update more effectively after stabilizing active chain
    val activeChain = apply()

    blocksInfoData.foldLeft(mainchainParent) {
      case (parent, (id, data)) =>
        activeChain.addToBothStorages(id, data, parent)
        data.mainchainBlockReferenceHashes.lastOption.orElse(parent)
    }
    activeChain
  }
}
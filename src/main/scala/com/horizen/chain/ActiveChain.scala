package com.horizen.chain

import scorex.util.ModifierId

import scala.collection.mutable.ArrayBuffer

class ActiveChain private(private val sidechainCache: ChainedData[ModifierId, SidechainBlockInfo],
                          private val mainchainCache: ChainedData[MainchainBlockReferenceId, MainchainBlockReferenceData]) {
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
  def mcHeightByMcId(mainChainReferenceId: MainchainBlockReferenceId): Option[Int] = mainchainCache.heightById(mainChainReferenceId)

  def mcIdByMcHeight(mcHeight: Int): Option[MainchainBlockReferenceId] = mainchainCache.idByHeight(mcHeight)

  def heightOfMc: Int = mainchainCache.height

  def dataOfMcByMcId (mainChainReferenceId: MainchainBlockReferenceId): Option[MainchainBlockReferenceData] = mainchainCache.dataById(mainChainReferenceId)

  // Mixed data retrieval
  def heightByMcId(mcId: MainchainBlockReferenceId): Option[Int] = {
    mainchainCache.dataById(mcId).map(_.sidechainHeight)
  }

  def idByMcId(mcId: MainchainBlockReferenceId): Option[ModifierId] = {
    heightByMcId(mcId).flatMap(sidechainCache.idByHeight)
  }

  // Add data
  def setBestBlock(newBestId: ModifierId, newBestData: SidechainBlockInfo, mainchainParent: Option[MainchainBlockReferenceId]): Unit = {
    // Every time when we have mainchain block references, appropriate parent shall be present as well
    val referenceDataEmptiness = newBestData.mainchainBlockReferenceHashes.isEmpty
    require(referenceDataEmptiness == mainchainParent.isEmpty,
      s"Active chain inconsistency: references data emptiness is ${referenceDataEmptiness} but mainchain parent emptiness is ${mainchainParent.isEmpty}")

    if (height != 0 && !sidechainCache.contains(newBestData.parentId)) {
      throw new IllegalArgumentException(s"Try to add unconnected sidechain block with id ${newBestId} to an active chain")
    }

    cutBothStorages(newBestId, newBestData, mainchainParent)

    // if mainchain had been cleared by cutting then new mainchain parent shall be provided by new best block
    val newMainchainParent = mainchainCache.bestId.orElse(mainchainParent)
    addToBothStorages(newBestId, newBestData, newMainchainParent)
  }

  private def cutBothStorages(newTipId: ModifierId, newTipInfo: SidechainBlockInfo, mainchainParent: Option[MainchainBlockReferenceId]): Unit = {
    sidechainCache.cutToId(newTipInfo.parentId)

    val mainchainCutPoint: Option[MainchainBlockReferenceId] = mainchainParent.orElse(getLastExistMainchainReferenceInSidechain)
    mainchainCutPoint match {
      case Some(parent) => mainchainCache.cutToId(parent)
      case None => mainchainCache.clear()
    }
  }

  private def getLastExistMainchainReferenceInSidechain: Option[MainchainBlockReferenceId] = {
    sidechainCache
      .getLastDataByPredicate(_.mainchainBlockReferenceHashes.nonEmpty)
      .map(_.mainchainBlockReferenceHashes.last)
  }

  private def addToBothStorages(newTipId: ModifierId, newTipInfo: SidechainBlockInfo, parentForMainchain: Option[MainchainBlockReferenceId]): Unit = {
    sidechainCache.appendData(newTipId, newTipInfo)

    val preparedMainchainReferences = buildMainchainBlockReferences(newTipInfo.mainchainBlockReferenceHashes, parentForMainchain)
    preparedMainchainReferences.foreach { case (id, data) => mainchainCache.appendData(id, data) }
  }

  private def buildMainchainBlockReferences(mainchainBlockHashes: Seq[MainchainBlockReferenceId], parentForMainchain: Option[MainchainBlockReferenceId]) = {
    if (mainchainBlockHashes.nonEmpty) {
      require(parentForMainchain.isDefined, "Active chain inconsistency: parent is not defined for new best non empty mainchain references")

      val bufferForBuildingMainchainBlockReferences = (Seq[(MainchainBlockReferenceId, MainchainBlockReferenceData)](), parentForMainchain.get)
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
    new ActiveChain(new ChainedData[ModifierId, SidechainBlockInfo](), new ChainedData[MainchainBlockReferenceId, MainchainBlockReferenceData]())
  }

  // In case of storage with blocks
  def apply(blocksInfoData: ArrayBuffer[(ModifierId, SidechainBlockInfo)], mainchainParent: Option[MainchainBlockReferenceId]): ActiveChain = {
    val firstMainchainReference = blocksInfoData
      .find{case (_, data) => data.mainchainBlockReferenceHashes.nonEmpty}
      .map{case (_, data) => data.mainchainBlockReferenceHashes.head}

    require(firstMainchainReference.isEmpty == mainchainParent.isEmpty,
    s"Inconsistency during loading active chain: mainchain reference presence is ${firstMainchainReference.isEmpty} and mainchain parent is ${mainchainParent.isEmpty}")

    val activeChain = apply()

    blocksInfoData.foldLeft(mainchainParent) {
      case (parent, (id, data)) =>
        activeChain.addToBothStorages(id, data, parent)
        data.mainchainBlockReferenceHashes.lastOption.orElse(parent)
    }
    activeChain
  }
}
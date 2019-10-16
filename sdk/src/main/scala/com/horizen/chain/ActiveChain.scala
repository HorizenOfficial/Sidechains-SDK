package com.horizen.chain

import scorex.util.ModifierId

import scala.collection.mutable.ArrayBuffer

final class ActiveChain private(private val sidechainCache: ElementsChain[ModifierId, SidechainBlockInfo],
                                private val mainchainCache: ElementsChain[MainchainBlockReferenceId, MainchainBlockReferenceData],
                                private val mainchainCreationBlockHeight: Int = 1) {

  require(mainchainCreationBlockHeight > 0, "Mainchain creation block height height shall be at least 1")
  private val mainchainCreationBlockHeightDifference = mainchainCreationBlockHeight - 1

  // Sidechain data retrieval
  def height: Int = sidechainCache.height

  def bestId: Option[ModifierId] = sidechainCache.bestId

  def bestScBlockInfo: Option[SidechainBlockInfo] = sidechainCache.bestData

  def heightById(id: ModifierId): Option[Int] = sidechainCache.heightById(id)

  def contains(id: ModifierId): Boolean = sidechainCache.contains(id)

  def chainAfter(id: ModifierId): Seq[ModifierId] = sidechainCache.chainAfter(id)

  def blockInfoById(id: ModifierId): Option[SidechainBlockInfo] = sidechainCache.heightById(id).flatMap(sidechainCache.dataByHeight)

  def blockInfoByHeight(blockHeight: Int): Option[SidechainBlockInfo] = sidechainCache.dataByHeight(blockHeight)

  def idByHeight(blockHeight: Int): Option[ModifierId] = sidechainCache.idByHeight(blockHeight)

  // Mainchain data retrieval
  def mcHeightByMcId(mainChainReferenceId: MainchainBlockReferenceId): Option[Int] = mainchainCache.heightById(mainChainReferenceId).map(_ + mainchainCreationBlockHeightDifference)

  def mcIdByMcHeight(mcHeight: Int): Option[MainchainBlockReferenceId] = mainchainCache.idByHeight(mcHeight - mainchainCreationBlockHeightDifference)

  // Active chain store
  def heightOfMc: Int = mainchainCache.height + mainchainCreationBlockHeightDifference

  def mcBlockReferenceDataByMcId(mainChainReferenceId: MainchainBlockReferenceId): Option[MainchainBlockReferenceData] = mainchainCache.dataById(mainChainReferenceId)

  // Mixed data retrieval
  def heightByMcId(mcId: MainchainBlockReferenceId): Option[Int] = {
    mainchainCache.dataById(mcId).map(_.sidechainHeight)
  }

  def idByMcId(mcId: MainchainBlockReferenceId): Option[ModifierId] = {
    heightByMcId(mcId).flatMap(sidechainCache.idByHeight)
  }

  def setBestBlock(newBestId: ModifierId, newBestData: SidechainBlockInfo, mainchainParentId: Option[MainchainBlockReferenceId]): Unit = {
    if (height == 0) {
      setGenesisBlock(newBestId, newBestData, mainchainParentId)
    }
    else {
      setNonGenesisBlock(newBestId, newBestData, mainchainParentId)
    }
  }

  private def setGenesisBlock(genesisBlockId: ModifierId, genesisBlockInfo: SidechainBlockInfo, mainchainParentId: Option[MainchainBlockReferenceId]): Unit = {
    if (height != 0) throw new IllegalArgumentException("Try to set genesis block for non-empty active chain")
    if (genesisBlockInfo.mainchainBlockReferenceHashes.isEmpty) throw new IllegalArgumentException("Mainchain block references shall be defined for genesis block")
    if (mainchainParentId.isEmpty) throw new IllegalArgumentException ("Parent for mainchain creation block shall be set")

    addToBothStorages(genesisBlockId, genesisBlockInfo, mainchainParentId)
  }

  private def setNonGenesisBlock(newBestId: ModifierId, newBestInfo: SidechainBlockInfo, givenMainchainParentId: Option[MainchainBlockReferenceId]): Unit = {
    // check sidechain correctness
    val parentHeight = heightById(newBestInfo.getParentId).getOrElse(throw new IllegalArgumentException(s"Try to add unconnected sidechain block with id ${newBestId} to an active chain"))

    // check mainchain correctness
    val actualMainchainParentForNewBlock = getLastMainchainReferenceBeforeHeight(parentHeight).getOrElse(throw new IllegalStateException(s"New best block clear all mainchain references"))

    val mcReferencesIsEmpty = newBestInfo.mainchainBlockReferenceHashes.isEmpty
    if (mcReferencesIsEmpty != givenMainchainParentId.isEmpty) {
      throw new IllegalArgumentException(s"Active chain inconsistency: mainchain references isEmpty are ${mcReferencesIsEmpty} but mainchain parent isEmpty are ${givenMainchainParentId.isEmpty}")
    }

    givenMainchainParentId.foreach{givenParentId =>
      if(givenParentId != actualMainchainParentForNewBlock) {
        throw new IllegalArgumentException("Try to add inconsistent mainchain block references")
      }
    }

    // cut both storages
    sidechainCache.cutToId(newBestInfo.parentId)
    mainchainCache.cutToId(actualMainchainParentForNewBlock)

    // add new data
    addToBothStorages(newBestId, newBestInfo, Some(actualMainchainParentForNewBlock))
  }

  private def addToBothStorages(newTipId: ModifierId, newTipInfo: SidechainBlockInfo, parentForMainchain: Option[MainchainBlockReferenceId]): Unit = {
    sidechainCache.appendData(newTipId, newTipInfo)

    val addedTipHeight = heightById(newTipId).getOrElse(throw new IllegalStateException("Added tip has no height"))
    val preparedMainchainReferences = buildMainchainBlockReferences(addedTipHeight, newTipInfo.mainchainBlockReferenceHashes, parentForMainchain)
    preparedMainchainReferences.foreach { case (id, data) => mainchainCache.appendData(id, data) }
  }

  private def getLastMainchainReferenceBeforeHeight(scHeight: Int): Option[MainchainBlockReferenceId] = {
    sidechainCache
      .getLastDataByPredicateBeforeHeight(scHeight)(_.mainchainBlockReferenceHashes.nonEmpty)
      .flatMap(scBlockInfo => scBlockInfo.mainchainBlockReferenceHashes.lastOption)
  }

  private def buildMainchainBlockReferences(sidechainHeight: Int, mainchainBlockHashes: Seq[MainchainBlockReferenceId], parentForMainchain: Option[MainchainBlockReferenceId]) = {
    if (mainchainBlockHashes.nonEmpty) {
      require(parentForMainchain.isDefined, "Active chain inconsistency: parent is not defined for new best non empty mainchain references")

      val bufferForBuildingMainchainBlockReferences = (Seq[(MainchainBlockReferenceId, MainchainBlockReferenceData)](), parentForMainchain.get)
      mainchainBlockHashes.foldLeft(bufferForBuildingMainchainBlockReferences) {
        case ((data, parent), reference) => (data :+ (reference, MainchainBlockReferenceData(sidechainHeight, parent)), reference)
      }._1
    }
    else {
      Seq()
    }
  }
}

object ActiveChain {
  // In case of empty storage
  def apply(mainchainCreationBlockHeight: Int): ActiveChain = {
    new ActiveChain(new ElementsChain[ModifierId, SidechainBlockInfo](), new ElementsChain[MainchainBlockReferenceId, MainchainBlockReferenceData](), mainchainCreationBlockHeight: Int)
  }

  // In case of storage with blocks
  def apply(blocksInfoData: ArrayBuffer[(ModifierId, SidechainBlockInfo)], mainchainParent: MainchainBlockReferenceId, mainchainCreationBlockHeight: Int): ActiveChain = {
    require(blocksInfoData.head._2.mainchainBlockReferenceHashes.nonEmpty, "Incorrect data for creation Active chain: first block shall contains mainchain block references")

    val activeChain = ActiveChain(mainchainCreationBlockHeight)

    blocksInfoData.foldLeft(Option(mainchainParent)) {
      case (parent, (id, data)) =>
        activeChain.addToBothStorages(id, data, parent)
        data.mainchainBlockReferenceHashes.lastOption.orElse(parent)
    }
    activeChain
  }
}
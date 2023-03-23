package io.horizen.chain

import sparkz.util.ModifierId

import scala.collection.mutable.ArrayBuffer


final class ActiveChain private(sidechainCache: ElementsChain[ModifierId, SidechainBlockInfo],
                                mainchainHeadersCache: ElementsChain[MainchainHeaderHash, MainchainHeaderMetadata],
                                mainchainReferenceDataCache: ElementsChain[MainchainHeaderHash, MainchainHeaderMetadata],
                                mainchainCreationBlockHeight: Int = 1) {

  require(mainchainCreationBlockHeight > 0, "Mainchain creation block height height shall be at least 1")
  private val mainchainCreationBlockHeightDifference = mainchainCreationBlockHeight - 1

  // Sidechain data retrieval
  def height: Int = sidechainCache.height

  def bestId: Option[ModifierId] = sidechainCache.bestId

  def bestScBlockInfo: Option[SidechainBlockInfo] = sidechainCache.bestData

  def heightById(id: ModifierId): Option[Int] = sidechainCache.heightById(id)

  def contains(id: ModifierId): Boolean = sidechainCache.contains(id)

  def chainAfter(id: ModifierId, limit: Option[Int]): Seq[ModifierId] = sidechainCache.chainAfter(id, limit)

  def blockInfoById(id: ModifierId): Option[SidechainBlockInfo] = sidechainCache.heightById(id).flatMap(sidechainCache.dataByHeight)

  def blockInfoByHeight(blockHeight: Int): Option[SidechainBlockInfo] = sidechainCache.dataByHeight(blockHeight)

  def idByHeight(blockHeight: Int): Option[ModifierId] = sidechainCache.idByHeight(blockHeight)

  // Mainchain data retrieval
  def mcHeadersHeightByMcHash(mainchainHeaderHash: MainchainHeaderHash): Option[Int] = mainchainHeadersCache.heightById(mainchainHeaderHash).map(_ + mainchainCreationBlockHeightDifference)

  def mcRefDataHeightByMcHash(mainchainHeaderHash: MainchainHeaderHash): Option[Int] = mainchainReferenceDataCache.heightById(mainchainHeaderHash).map(_ + mainchainCreationBlockHeightDifference)

  def mcHashByMcHeight(mcHeight: Int): Option[MainchainHeaderHash] = mainchainHeadersCache.idByHeight(mcHeight - mainchainCreationBlockHeightDifference)

  // Active chain store
  def heightOfMcHeaders: Int = mainchainHeadersCache.height + mainchainCreationBlockHeightDifference

  def heightOfMcReferencesData: Int = mainchainReferenceDataCache.height + mainchainCreationBlockHeightDifference

  def mcHeaderMetadataByMcHash(mainchainHeaderHash: MainchainHeaderHash): Option[MainchainHeaderMetadata] = mainchainHeadersCache.dataById(mainchainHeaderHash)

  def mcReferenceDataMetadataByMcHash(mainchainHeaderHash: MainchainHeaderHash): Option[MainchainHeaderMetadata] = mainchainReferenceDataCache.dataById(mainchainHeaderHash)

  def bestMainchainHeaderHash: Option[MainchainHeaderHash] = mainchainHeadersCache.bestId

  // Mixed data retrieval
  def heightByMcHeader(mainchainHeaderHash: MainchainHeaderHash): Option[Int] = {
    mainchainHeadersCache.dataById(mainchainHeaderHash).map(_.sidechainHeight)
  }

  def idByMcHeader(mainchainHeaderHash: MainchainHeaderHash): Option[ModifierId] = {
    heightByMcHeader(mainchainHeaderHash).flatMap(sidechainCache.idByHeight)
  }

  def heightByMcReferenceData(mainchainReferenceDataHeaderHash: MainchainHeaderHash): Option[Int] = {
    mainchainReferenceDataCache.dataById(mainchainReferenceDataHeaderHash).map(_.sidechainHeight)
  }

  def idByMcReferenceData(mainchainReferenceDataHeaderHash: MainchainHeaderHash): Option[ModifierId] = {
    heightByMcReferenceData(mainchainReferenceDataHeaderHash).flatMap(sidechainCache.idByHeight)
  }

  def setBestBlock(newBestId: ModifierId, newBestData: SidechainBlockInfo, mainchainParentHashOpt: Option[MainchainHeaderHash]): Unit = {
    if (height == 0) {
      setGenesisBlock(newBestId, newBestData, mainchainParentHashOpt)
    }
    else {
      setNonGenesisBlock(newBestId, newBestData, mainchainParentHashOpt)
    }
  }

  private def setGenesisBlock(genesisBlockId: ModifierId, genesisBlockInfo: SidechainBlockInfo, mainchainParentHashOpt: Option[MainchainHeaderHash]): Unit = {
    if (height != 0) throw new IllegalArgumentException("Try to set genesis block for non-empty active chain")
    if (genesisBlockInfo.mainchainHeaderHashes.isEmpty) throw new IllegalArgumentException("Mainchain block headers must be defined for genesis block")
    if (genesisBlockInfo.mainchainReferenceDataHeaderHashes.isEmpty) throw new IllegalArgumentException("Mainchain block reference data must be defined for genesis block")
    if (mainchainParentHashOpt.isEmpty) throw new IllegalArgumentException ("Parent for mainchain creation block shall be set")

    // Parent for both first MainchainBlockReferenceData and MainchainHeader is the same for genesis block
    addToStorages(genesisBlockId, genesisBlockInfo, mainchainParentHashOpt, mainchainParentHashOpt)
  }

  private def setNonGenesisBlock(newBestId: ModifierId, newBestInfo: SidechainBlockInfo, givenMainchainParentHashOpt: Option[MainchainHeaderHash]): Unit = {
    // check sidechain correctness
    val parentHeight = heightById(newBestInfo.getParentId).getOrElse(throw new IllegalArgumentException(s"Try to add unconnected sidechain block with id ${newBestId} to an active chain"))

    // check mainchain headers correctness
    val actualMainchainParentForNewBlock = getLastMainchainHeaderHashTillHeight(parentHeight).getOrElse(throw new IllegalStateException(s"New best block clear all mainchain headers"))

    val mcReferencesIsEmpty = newBestInfo.mainchainHeaderHashes.isEmpty
    if (mcReferencesIsEmpty != givenMainchainParentHashOpt.isEmpty) {
      throw new IllegalArgumentException(s"Active chain inconsistency: mainchain references isEmpty are ${mcReferencesIsEmpty} but mainchain parent isEmpty are ${givenMainchainParentHashOpt.isEmpty}")
    }

    givenMainchainParentHashOpt.foreach{givenParentHash =>
      if(givenParentHash != actualMainchainParentForNewBlock) {
        throw new IllegalArgumentException("Try to add inconsistent mainchain headers")
      }
    }

    // get actual MainchainReferenceData HeaderHash till parentHeight
    val actualMainchainReferenceDataParentForNewBlock = getLastMainchainReferenceDataHeaderHashTillHeight(parentHeight)
      .getOrElse(throw new IllegalStateException(s"New best block clear all mainchain references data header hashes"))

    // check MainchainReferenceData correctness against MainchainHeaders
    val mcDataHeight: Int = mcRefDataHeightByMcHash(actualMainchainReferenceDataParentForNewBlock).get
    val mcHeaderHeight: Int = mcHeadersHeightByMcHash(actualMainchainParentForNewBlock).get
    val missedRefDataHeaderHashes: Seq[MainchainHeaderHash] =
      (mcDataHeight + 1 to mcHeaderHeight).flatMap(h => mcHashByMcHeight(h)) ++ newBestInfo.mainchainHeaderHashes
    val expectedRefDataHeaderHashes = missedRefDataHeaderHashes.take(newBestInfo.mainchainReferenceDataHeaderHashes.size)
    if(!expectedRefDataHeaderHashes.equals(newBestInfo.mainchainReferenceDataHeaderHashes))
      throw new IllegalArgumentException("Try to add inconsistent mainchain reference data")


    // cut storages
    sidechainCache.cutToId(newBestInfo.parentId)
    mainchainHeadersCache.cutToId(actualMainchainParentForNewBlock)
    mainchainReferenceDataCache.cutToId(actualMainchainReferenceDataParentForNewBlock)

    // add new data
    addToStorages(newBestId, newBestInfo, Some(actualMainchainParentForNewBlock), Some(actualMainchainReferenceDataParentForNewBlock))
  }

  private def addToStorages(newTipId: ModifierId,
                            newTipInfo: SidechainBlockInfo,
                            mainchainHeaderParentHashOpt: Option[MainchainHeaderHash],
                            mainchainRefDataParentHeaderHash: Option[MainchainHeaderHash]): Unit = {
    sidechainCache.appendData(newTipId, newTipInfo)

    val addedTipHeight = heightById(newTipId).getOrElse(throw new IllegalStateException("Added tip has no height"))
    val preparedMainchainHeadersInfo = buildMainchainHeadersInfo(addedTipHeight, newTipInfo.mainchainHeaderHashes, mainchainHeaderParentHashOpt)
    preparedMainchainHeadersInfo.foreach { case (id, data) => mainchainHeadersCache.appendData(id, data) }

    val preparedMainchainRefDataHeadersInfo = buildMainchainHeadersInfo(addedTipHeight, newTipInfo.mainchainReferenceDataHeaderHashes, mainchainRefDataParentHeaderHash)
    preparedMainchainRefDataHeadersInfo.foreach { case (id, data) => mainchainReferenceDataCache.appendData(id, data) }
  }

  private def getLastMainchainHeaderHashTillHeight(scHeight: Int): Option[MainchainHeaderHash] = {
    sidechainCache
      .getLastDataByPredicateTillHeight(scHeight)(_.mainchainHeaderHashes.nonEmpty)
      .flatMap(scBlockInfo => scBlockInfo.mainchainHeaderHashes.lastOption)
  }

  private def getLastMainchainReferenceDataHeaderHashTillHeight(scHeight: Int): Option[MainchainHeaderHash] = {
    sidechainCache
      .getLastDataByPredicateTillHeight(scHeight)(_.mainchainReferenceDataHeaderHashes.nonEmpty)
      .flatMap(scBlockInfo => scBlockInfo.mainchainReferenceDataHeaderHashes.lastOption)
  }

  private def buildMainchainHeadersInfo(sidechainHeight: Int,
                                        mainchainHeaderHashes: Seq[MainchainHeaderHash],
                                        mainchainParentHashOpt: Option[MainchainHeaderHash]): Seq[(MainchainHeaderHash, MainchainHeaderMetadata)] = {
    if (mainchainHeaderHashes.nonEmpty) {
      require(mainchainParentHashOpt.isDefined, "Active chain inconsistency: parent is not defined for new best non empty mainchain references")

      val bufferForBuildingMainchainHeaders = (Seq[(MainchainHeaderHash, MainchainHeaderMetadata)](), mainchainParentHashOpt.get)
      mainchainHeaderHashes.foldLeft(bufferForBuildingMainchainHeaders) {
        case ((data, parent), headerHash) => (data :+ (headerHash, MainchainHeaderMetadata(sidechainHeight, parent)), headerHash)
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
    new ActiveChain(
      new ElementsChain[ModifierId, SidechainBlockInfo](),
      new ElementsChain[MainchainHeaderHash, MainchainHeaderMetadata](),
      new ElementsChain[MainchainHeaderHash, MainchainHeaderMetadata](),
      mainchainCreationBlockHeight: Int)
  }

  // In case of storage with blocks
  def apply(blocksInfoData: ArrayBuffer[(ModifierId, SidechainBlockInfo)], mainchainParentHash: MainchainHeaderHash, mainchainCreationBlockHeight: Int): ActiveChain = {
    require(blocksInfoData.head._2.mainchainHeaderHashes.nonEmpty, "Incorrect data for creation Active chain: first block shall contains mainchain block references")

    val activeChain = ActiveChain(mainchainCreationBlockHeight)

    blocksInfoData.foldLeft((Option(mainchainParentHash), Option(mainchainParentHash))) {
      case ((mainchainHeaderParentHashOpt, mainchainRefDataParentHeaderHashOpt), (id, data)) =>
        activeChain.addToStorages(id, data, mainchainHeaderParentHashOpt, mainchainRefDataParentHeaderHashOpt)

        val newMainchainHeaderParentHashOpt = data.mainchainHeaderHashes.lastOption.orElse(mainchainHeaderParentHashOpt)
        val newMainchainRefDataParentHeaderHashOpt = data.mainchainReferenceDataHeaderHashes.lastOption.orElse(mainchainRefDataParentHeaderHashOpt)
        (newMainchainHeaderParentHashOpt, newMainchainRefDataParentHeaderHashOpt)
    }
    activeChain
  }
}
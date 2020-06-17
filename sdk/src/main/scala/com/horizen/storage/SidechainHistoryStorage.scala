package com.horizen.storage

import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.block.{SidechainBlockSerializer, _}
import com.horizen.chain.{MainchainBlockReferenceDataInfo, _}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.NetworkParams
import com.horizen.utils._
import com.horizen.utils.{Pair => JPair}
import scorex.core.consensus.ModifierSemanticValidity
import scorex.crypto.hash.Blake2b256
import scorex.util.{ModifierId, ScorexLogging, bytesToId, idToBytes}

import scala.collection.mutable.ArrayBuffer
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Random, Success, Try}


trait SidechainBlockInfoProvider {
  def blockInfoById(blockId: ModifierId): SidechainBlockInfo
}

class SidechainHistoryStorage(storage: Storage, sidechainTransactionsCompanion: SidechainTransactionsCompanion, params: NetworkParams)
  extends SidechainBlockInfoProvider
  with ScorexLogging {
  // Version - RandomBytes(32)

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainTransactionsCompanion != null, "SidechainTransactionsCompanion must be NOT NULL.")
  require(params != null, "params must be NOT NULL.")

  private val sidechainBlockSerializer = new SidechainBlockSerializer(sidechainTransactionsCompanion)
  private val bestBlockIdKey: ByteArrayWrapper = new ByteArrayWrapper(Array.fill(32)(-1: Byte))

  private val activeChain: ActiveChain = loadActiveChain()

  private def loadActiveChain(): ActiveChain = {
    if (storage.isEmpty) {
      return ActiveChain(params.mainchainCreationBlockHeight)
    }

    val activeChainBlocksInfo: ArrayBuffer[(ModifierId, SidechainBlockInfo)] = new ArrayBuffer()

    activeChainBlocksInfo.append((bestBlockId, blockInfoByIdFromStorage(bestBlockId)))
    while (activeChainBlocksInfo.last._2.height > 1) {
      val id = activeChainBlocksInfo.last._2.parentId
      activeChainBlocksInfo.append((id, blockInfoByIdFromStorage(id)))
    }

    val orderedChainBlocks = activeChainBlocksInfo.reverse

    val mainchainBlockParent = for {
      firstSidechainBlockInfo <- orderedChainBlocks.headOption
      firstSidechainBlock <- blockById(firstSidechainBlockInfo._1)
      firstMainchainHeader <- firstSidechainBlock.mainchainHeaders.headOption
    } yield byteArrayToMainchainHeaderHash(firstMainchainHeader.hashPrevBlock)

    ActiveChain(orderedChainBlocks, mainchainBlockParent.getOrElse(throw new IllegalStateException("Loaded active chain miss mainchain parent")), params.mainchainCreationBlockHeight)
  }

  private def blockInfoKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"blockInfo$blockId"))

  private def nextVersion: Array[Byte] = {
    val version = new Array[Byte](32)
    Random.nextBytes(version)
    version
  }

  def height: Int = activeChain.height

  def heightOf(blockId: ModifierId): Option[Int] = {
    blockInfoOptionById(blockId).map(_.height)
  }

  def bestBlockId: ModifierId = storage.get(bestBlockIdKey).asScala.map(d => bytesToId(d.data)).getOrElse(params.sidechainGenesisBlockId)

  def bestBlock: SidechainBlock = {
    require(height > 0, "SidechainHistoryStorage is empty. Cannot retrieve best block.")
    blockById(bestBlockId).get
  }

  def bestBlockInfo: SidechainBlockInfo = {
    require(height > 0, "SidechainHistoryStorage is empty. Cannot retrieve best block.")
    blockInfoById(bestBlockId)
  }

  def blockById(blockId: ModifierId): Option[SidechainBlock] = {
    val blockIdBytes = new ByteArrayWrapper(idToBytes(blockId))
    storage.get(blockIdBytes).asScala.flatMap(baw => sidechainBlockSerializer.parseBytesTry(baw.data).toOption)
  }

  //Block info shall be in history storage, otherwise something going totally wrong
  def blockInfoById(blockId: ModifierId): SidechainBlockInfo = {
    blockInfoOptionById(blockId).getOrElse(throw new IllegalStateException(s"No block info for block ${blockId}"))
  }

  def blockInfoOptionById(blockId: ModifierId): Option[SidechainBlockInfo] = {
    activeChain.blockInfoById(blockId).orElse(blockInfoOptionByIdFromStorage(blockId))
  }

  private def blockInfoOptionByIdFromStorage(blockId: ModifierId): Option[SidechainBlockInfo] = {
    storage.get(blockInfoKey(blockId)).asScala.flatMap(baw => SidechainBlockInfoSerializer.parseBytesTry(baw.data).toOption)
  }

  private def blockInfoByIdFromStorage(blockId: ModifierId): SidechainBlockInfo = {
    blockInfoOptionByIdFromStorage(blockId).getOrElse(throw new IllegalArgumentException(s"No blockInfo in storage for blockId ${blockId}"))
  }

  def parentBlockId(blockId: ModifierId): Option[ModifierId] = blockInfoOptionById(blockId).map(_.parentId)

  def chainScoreFor(blockId: ModifierId): Option[Long] = blockInfoOptionById(blockId).map(_.score)

  def isInActiveChain(blockId: ModifierId): Boolean = activeChain.contains(blockId)

  def activeChainBlockId(height: Int): Option[ModifierId] = activeChain.idByHeight(height)

  def activeChainAfter(blockId: ModifierId): Seq[ModifierId] = activeChain.chainAfter(blockId)

  def getSidechainBlockContainingMainchainHeader(mainchainHeaderHash: Array[Byte]): Option[SidechainBlock] = {
    activeChain.idByMcHeader(byteArrayToMainchainHeaderHash(mainchainHeaderHash)).flatMap(blockById)
  }

  def getSidechainBlockContainingMainchainReferenceData(mainchainHeaderHash: Array[Byte]): Option[SidechainBlock] = {
    activeChain.idByMcReferenceData(byteArrayToMainchainHeaderHash(mainchainHeaderHash)).flatMap(blockById)
  }

  def getMainchainBlockReferenceByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReference] = {
    for {
      header <- getMainchainHeaderByHash(mainchainHeaderHash)
      data <- getMainchainReferenceDataByHash(mainchainHeaderHash)
    } yield MainchainBlockReference(header, data)
  }

  def getMainchainHeaderByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainHeader] = {
    val sidechainBlock: Option[SidechainBlock] = getSidechainBlockContainingMainchainHeader(mainchainHeaderHash)
    sidechainBlock.flatMap(_.mainchainHeaders.find(header => mainchainHeaderHash.sameElements(header.hash)))
  }

  def getMainchainReferenceDataByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceData] = {
    val sidechainBlock: Option[SidechainBlock] = getSidechainBlockContainingMainchainReferenceData(mainchainHeaderHash)
    sidechainBlock.flatMap(_.mainchainBlockReferencesData.find(data => mainchainHeaderHash.sameElements(data.headerHash)))
  }

  def getMainchainBlockReferenceInfoByMainchainBlockHeight(mainchainHeight: Int): Option[MainchainBlockReferenceInfo] = {
    activeChain.mcHashByMcHeight(mainchainHeight).flatMap(hash => getMainchainBlockReferenceInfoByHash(hash))
  }

  def getBestMainchainBlockReferenceInfo: Option[MainchainBlockReferenceInfo] = {
    getMainchainBlockReferenceInfoByMainchainBlockHeight(activeChain.heightOfMcReferencesData)
  }

  def getMainchainBlockReferenceInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceInfo] = {
    val mcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(mainchainHeaderHash)
    for {
      mcHeight <- activeChain.mcRefDataHeightByMcHash(mcHash)
      headerContainingId <- activeChain.idByMcHeader(mcHash)
      dataContainingId <- activeChain.idByMcReferenceData(mcHash)
      mcMetadata <- activeChain.mcHeaderMetadataByMcHash(mcHash)
    } yield buildMainchainBlockReferenceInfo(mcHash, mcMetadata, mcHeight, headerContainingId, dataContainingId)
  }

  private def buildMainchainBlockReferenceInfo(mcHash: MainchainHeaderHash,
                                               referenceInfo: MainchainHeaderMetadata,
                                               mcBlockHeight: Int,
                                               mainchainHeaderSidechainBlockId: ModifierId,
                                               mainchainReferenceDataSidechainBlockId: ModifierId): MainchainBlockReferenceInfo = {
    new MainchainBlockReferenceInfo(mcHash, referenceInfo.getParentId, mcBlockHeight, idToBytes(mainchainHeaderSidechainBlockId), idToBytes(mainchainReferenceDataSidechainBlockId))
  }

  def getMainchainHashesForIndexes(mainchainHeights: Seq[Int]): Seq[MainchainHeaderHash] = {
    mainchainHeights.flatMap(mainchainHeight => activeChain.mcHashByMcHeight(mainchainHeight))
  }

  def getBestMainchainHeaderInfo: Option[MainchainHeaderInfo] = {
    getMainchainHeaderInfoByHeight(activeChain.heightOfMcHeaders)
  }

  def getMainchainHeaderInfoByHeight(mainchainHeight: Int): Option[MainchainHeaderInfo] = {
    for {
      mcHash <- activeChain.mcHashByMcHeight(mainchainHeight)
    } yield getMainchainHeaderInfoByHash(mcHash).get
  }

  def getMainchainHeaderInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainHeaderInfo] = {
    val mcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(mainchainHeaderHash)
    for {
      mcHeight <- activeChain.mcHeadersHeightByMcHash(mcHash)
      sidechainBlockId <- activeChain.idByMcHeader(mcHash)
      mcMetadata <- activeChain.mcHeaderMetadataByMcHash(mcHash)
    } yield MainchainHeaderInfo(mcHash, mcMetadata.getParentId, mcHeight, sidechainBlockId)
  }

  def getBestMainchainBlockReferenceDataInfo: Option[MainchainBlockReferenceDataInfo] = {
    getMainchainBlockReferenceDataInfoByHeight(activeChain.heightOfMcReferencesData)
  }

  def getMainchainBlockReferenceDataInfoByHeight(mainchainHeight: Int): Option[MainchainBlockReferenceDataInfo] = {
    for {
      mcHash <- activeChain.mcHashByMcHeight(mainchainHeight)
    } yield getMainchainBlockReferenceDataInfoByHash(mcHash).get
  }

  def getMainchainBlockReferenceDataInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceDataInfo] = {
    val mcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(mainchainHeaderHash)
    for {
      mcHeight <- activeChain.mcRefDataHeightByMcHash(mcHash)
      sidechainBlockId <- activeChain.idByMcReferenceData(mcHash)
    } yield MainchainBlockReferenceDataInfo(mcHash, mcHeight, sidechainBlockId)
  }

  def update(block: SidechainBlock, blockInfo: SidechainBlockInfo): Try[SidechainHistoryStorage] = Try {
    require(block != null, "SidechainBlock must be NOT NULL.")
    require(block.parentId == blockInfo.parentId, "Passed BlockInfo data conflicts to passed Block.")

    val toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = new JArrayList()

    // add short block info
    toUpdate.add(new JPair(new ByteArrayWrapper(blockInfoKey(block.id)), new ByteArrayWrapper(blockInfo.bytes)))

    // add block
    toUpdate.add(new JPair(new ByteArrayWrapper(idToBytes(block.id)), new ByteArrayWrapper(block.bytes)))

    storage.update(
      new ByteArrayWrapper(nextVersion),
      toUpdate,
      new JArrayList[ByteArrayWrapper]())

    this
  }

  def semanticValidity(blockId: ModifierId): ModifierSemanticValidity = {
    blockInfoOptionById(blockId) match {
      case Some(info) => info.semanticValidity
      case None => ModifierSemanticValidity.Absent
    }
  }

  def updateSemanticValidity(block: SidechainBlock, status: ModifierSemanticValidity): Try[SidechainHistoryStorage] = Try {
    // if it's not a part of active chain, retrieve previous info from disk storage
    val oldInfo: SidechainBlockInfo = activeChain.blockInfoById(block.id).getOrElse(blockInfoById(block.id))
    val blockInfo = oldInfo.copy(semanticValidity = status)

    storage.update(
      new ByteArrayWrapper(nextVersion),
      java.util.Arrays.asList(new JPair(new ByteArrayWrapper(blockInfoKey(block.id)), new ByteArrayWrapper(blockInfo.bytes))),
      new JArrayList()
    )
    this
  }

  def setAsBestBlock(block: SidechainBlock, blockInfo: SidechainBlockInfo): Try[SidechainHistoryStorage] = Try {
    storage.update(
      new ByteArrayWrapper(nextVersion),
      java.util.Arrays.asList(new JPair(bestBlockIdKey, new ByteArrayWrapper(idToBytes(block.id)))),
      new JArrayList()
    )

    val mainchainParent: Option[MainchainHeaderHash] = block.mainchainHeaders.headOption.map(header => byteArrayToMainchainHeaderHash(header.hashPrevBlock))
    activeChain.setBestBlock(block.id, blockInfo, mainchainParent)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}

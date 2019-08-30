package com.horizen.storage

import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.block.{MainchainBlockReference, SidechainBlock, SidechainBlockSerializer}
import com.horizen.chain.{ActiveChain, SidechainBlockInfo, SidechainBlockInfoSerializer}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.NetworkParams
import com.horizen.utils._
import javafx.util.{Pair => JPair}
import scorex.core.consensus.ModifierSemanticValidity
import scorex.crypto.hash.Blake2b256
import scorex.util.{ModifierId, ScorexLogging, bytesToId, idToBytes}

import scala.collection.mutable.ArrayBuffer
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Random, Success, Try}



class SidechainHistoryStorage(storage: Storage, sidechainTransactionsCompanion: SidechainTransactionsCompanion, params: NetworkParams)
  extends ScorexLogging {
  // Version - RandomBytes(32)

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainTransactionsCompanion != null, "SidechainTransactionsCompanion must be NOT NULL.")
  require(params != null, "params must be NOT NULL.")

  private val bestBlockIdKey: ByteArrayWrapper = new ByteArrayWrapper(Array.fill(32)(-1: Byte))

  private val activeChain: ActiveChain = loadActiveChain()

  private def loadActiveChain(): ActiveChain = {
    if (height == 0)
      return ActiveChain()
    val activeChainBlocksInfo: ArrayBuffer[(ModifierId, SidechainBlockInfo)] = new ArrayBuffer(height)

    activeChainBlocksInfo.append((bestBlockId, blockInfoById(bestBlockId).get))
    while (activeChainBlocksInfo.last._2.height > 1) {
      val id = activeChainBlocksInfo.last._2.parentId
      activeChainBlocksInfo.append((id, blockInfoById(id).get))
    }

    val orderedChainBlocks = activeChainBlocksInfo.reverse

    val mainchainBlockParent = for {
      firstSidechainBlockInfo <- orderedChainBlocks.headOption
      firstSidechainBlock <- blockById(firstSidechainBlockInfo._1)
      firstMainchainBlockReference <- firstSidechainBlock.mainchainBlocks.headOption
    } yield firstMainchainBlockReference.header.hashPrevBlock

    ActiveChain(orderedChainBlocks, mainchainBlockParent.map(new ByteArrayWrapper(_)))
  }

  private def validityKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"validity$blockId"))

  private def blockInfoKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"blockInfo$blockId"))

  private def nextVersion: Array[Byte] = {
    val version = new Array[Byte](32)
    Random.nextBytes(version)
    version
  }

  def height: Int = heightOf(bestBlockId).getOrElse(0)

  def ScHeightByMcId(byteArrayWrapper: ByteArrayWrapper): Option[Int] = {
    activeChain.heightByMcId(byteArrayWrapper)
  }

  def heightOf(blockId: ModifierId): Option[Int] = {
    blockInfoById(blockId).map(_.height)
  }

  def bestBlockId: ModifierId = storage.get(bestBlockIdKey).asScala.map(d => bytesToId(d.data)).getOrElse(params.sidechainGenesisBlockId)

  def bestBlockData: Option[(ModifierId, SidechainBlockInfo)] = for (id <- activeChain.bestId; info <- activeChain.bestData) yield (id, info)

  def blockById(height: Integer): Option[(ModifierId, SidechainBlockInfo)] = {
    for (id <- activeChain.idByHeight(height); info <- activeChain.dataByHeight(height)) yield (id, info)
  }

  def bestBlock: SidechainBlock = {
    require(height > 0, "SidechainHistoryStorage is empty. Cannot retrieve best block.")
    blockById(bestBlockId).get
  }

  def blockById(blockId: ModifierId): Option[SidechainBlock] = {
    storage.get(new ByteArrayWrapper(idToBytes(blockId))).asScala.flatMap { baw =>
      val tryBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytesTry(baw.data)
      tryBlock match {
        case Failure(e) => log.warn("SidechainHistoryStorage: Failed to parse block bytes from storage.", e)
        case _ =>
      }
      tryBlock.toOption
    }
  }

  def blockInfoById(blockId: ModifierId): Option[SidechainBlockInfo] = {
    if (activeChain != null && activeChain.contains(blockId))
      return activeChain.dataById(blockId)

    storage.get(blockInfoKey(blockId)).asScala match {
      case Some(baw) => SidechainBlockInfoSerializer.parseBytesTry(baw.data) match {
        case Failure(e) =>
          log.warn("SidechainHistoryStorage: Failed to parse block info bytes from storage.", e)
          None
        case Success(blockInfo) => Some(blockInfo)
      }
      case None => None
    }
  }

  def parentBlockId(blockId: ModifierId): Option[ModifierId] = {
    blockInfoById(blockId) match {
      case Some(info) => Some(info.parentId)
      case None => None
    }
  }

  def chainScoreFor(blockId: ModifierId): Option[Long] = {
    blockInfoById(blockId) match {
      case Some(info) => Some(info.score)
      case None => None
    }
  }

  def isInActiveChain(blockId: ModifierId): Boolean = activeChain.contains(blockId)

  def activeChainBlockId(height: Int): Option[ModifierId] = activeChain.idByHeight(height)

  def activeChainFrom(blockId: ModifierId): Seq[ModifierId] = activeChain.chainFrom(blockId)

  def getSidechainBlockByMainchainBlockReferenceHash(mainchainBlockReferenceHash: Array[Byte]): Option[SidechainBlock] = {
    activeChain.idByMcId(mainchainBlockReferenceHash).flatMap(blockById)
  }

  def getMainchainBlockReferenceByHash(mainchainBlockReferenceHash: Array[Byte]): Option[MainchainBlockReference] = {
    val sidechainBlock: Option[SidechainBlock] = getSidechainBlockByMainchainBlockReferenceHash(mainchainBlockReferenceHash)
    sidechainBlock.flatMap(_.mainchainBlocks.find(d => mainchainBlockReferenceHash.sameElements(d.hash)))
  }

  def getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoHeight(mainchainHeight: Int): Option[MainchainBlockReferenceInfo] = {
    getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoGenesisHeight(mainchainHeight - params.genesisMainchainBlockHeight)
  }

  def getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoGenesisHeight(mainchainGenesisHeight: Int): Option[MainchainBlockReferenceInfo] = {
    for {
      mcId <- activeChain.mcIdByMcHeight(mainchainGenesisHeight)
      id <- activeChain.idByMcId(mcId)
      mcData <- activeChain.dataOfMcByMcId(mcId)
    } yield (new MainchainBlockReferenceInfo(mcId, mcData.getParentId, height, id.getBytes))
  }

  def getBestMainchainBlockReferenceInfo: Option[MainchainBlockReferenceInfo] = {
    getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoGenesisHeight(activeChain.heightOfMc)
  }

  def getMainchainBlockReferenceInfoByHash(mcId: Array[Byte]): Option[MainchainBlockReferenceInfo] = {
    for {
      height <- activeChain.mcHeightByMcId(mcId)
      id <- activeChain.idByMcId(mcId)
      mcData <- activeChain.dataOfMcByMcId(mcId)
    } yield (new MainchainBlockReferenceInfo(mcId, mcData.getParentId, height, id.getBytes))
  }

  def update(block: SidechainBlock, chainScore: Long): Try[SidechainHistoryStorage] = Try {
    require(block != null, "SidechainBlock must be NOT NULL.")

    val toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = new JArrayList()

    // add short block info
    val blockInfo: SidechainBlockInfo = SidechainBlockInfo(
      heightOf(block.parentId).getOrElse(0) + 1, // to do: check, what for orphan blocks?
      chainScore,
      block.parentId,
      ModifierSemanticValidity.Unknown,
      block.mainchainBlocks.map(_.hash)
    )

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
    blockInfoById(blockId) match {
      case Some(info) => info.semanticValidity
      case None => ModifierSemanticValidity.Absent
    }
  }

  def updateSemanticValidity(block: SidechainBlock, status: ModifierSemanticValidity): Try[SidechainHistoryStorage] = Try {
    // if it's not a part of active chain, retrieve previous info from disk storage
    val oldInfo: SidechainBlockInfo = activeChain.dataById(block.id).getOrElse(blockInfoById(block.id).get)
    val blockInfo = oldInfo.copy(semanticValidity = status)

    storage.update(
      new ByteArrayWrapper(nextVersion),
      java.util.Arrays.asList(new JPair(new ByteArrayWrapper(blockInfoKey(block.id)), new ByteArrayWrapper(blockInfo.bytes))),
      new JArrayList()
    )
    //activeChain.updateSemanticValidity(block.id, status)
    this
  }

  def setAsBestBlock(block: SidechainBlock, blockInfo: SidechainBlockInfo): Try[SidechainHistoryStorage] = Try {
    storage.update(
      new ByteArrayWrapper(nextVersion),
      java.util.Arrays.asList(new JPair(bestBlockIdKey, new ByteArrayWrapper(idToBytes(block.id)))),
      new JArrayList()
    )

    val mainchainParent: Option[ByteArrayWrapper] = block.mainchainBlocks.headOption.map(_.header.hashPrevBlock)
    activeChain.setBestBlock(block.id, blockInfo, mainchainParent)
    this
  }
}

package com.horizen.storage

import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.utils.ByteArrayWrapper
import scorex.util.{bytesToId, idToBytes}
import scorex.core.consensus.ModifierSemanticValidity
import scorex.crypto.hash.Blake2b256
import scorex.util.{ModifierId, ScorexLogging}
import javafx.util.{Pair => JPair}

import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Random, Success, Try}
import com.horizen.chain.{ActiveChain, SidechainBlockInfo, SidechainBlockInfoSerializer}

import scala.collection.mutable.ArrayBuffer


class SidechainHistoryStorage(storage: Storage, sidechainTransactionsCompanion: SidechainTransactionsCompanion, params: NetworkParams)
  extends ScorexLogging {
  // Version - RandomBytes(32)

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainTransactionsCompanion != null, "SidechainTransactionsCompanion must be NOT NULL.")
  require(params != null, "params must be NOT NULL.")

  private val bestBlockIdKey: ByteArrayWrapper = new ByteArrayWrapper(Array.fill(32)(-1: Byte))

  private val activeChain: ActiveChain = loadActiveChain()

  private def loadActiveChain(): ActiveChain = {
    if(height == 0)
      return ActiveChain()
    val activeChainBlocksInfo: ArrayBuffer[(ModifierId, SidechainBlockInfo)] = new ArrayBuffer(height)

    activeChainBlocksInfo.append((bestBlockId, blockInfoById(bestBlockId).get))
    while(activeChainBlocksInfo.last._2.height > 1) {
      val id = activeChainBlocksInfo.last._2.parentId
      activeChainBlocksInfo.append((id, blockInfoById(id).get))
    }

    ActiveChain(activeChainBlocksInfo.reverse)
  }

  private def validityKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"validity$blockId"))

  private def blockInfoKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"blockInfo$blockId"))

  private def nextVersion: Array[Byte] = {
    val version = new Array[Byte](32)
    Random.nextBytes(version)
    version
  }


  def height: Int = heightOf(bestBlockId).getOrElse(0)

  def heightOf(blockId: ModifierId): Option[Int] = {
    blockInfoById(blockId) match {
      case Some(info) => Some(info.height)
      case None => None
    }
  }

  def bestBlockId: ModifierId = storage.get(bestBlockIdKey).asScala.map(d => bytesToId(d.data)).getOrElse(params.sidechainGenesisBlockId)

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
    if(activeChain != null && activeChain.contains(blockId))
      return activeChain.getBlockInfo(blockId)
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

  def activeChainBlockId(height: Int): Option[ModifierId] = activeChain.getBlockId(height)

  def activeChainFrom(blockId: ModifierId): Seq[ModifierId] = activeChain.chainFrom(blockId)

  def update(block: SidechainBlock, chainScore: Long): Try[SidechainHistoryStorage] = Try {
    require(block != null, "SidechainBlock must be NOT NULL.")

    val toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = new JArrayList()

    // add short block info
    val blockInfo: SidechainBlockInfo = SidechainBlockInfo(
      heightOf(block.parentId).getOrElse(0) + 1, // to do: check, what for orphan blocks?
      chainScore,
      block.parentId,
      ModifierSemanticValidity.Unknown
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
    val oldInfo: SidechainBlockInfo = activeChain.getBlockInfo(block.id).getOrElse(blockInfoById(block.id).get)
    val blockInfo = SidechainBlockInfo(oldInfo.height, oldInfo.score, oldInfo.parentId, status)

    storage.update(
      new ByteArrayWrapper(nextVersion),
      java.util.Arrays.asList(new JPair(new ByteArrayWrapper(blockInfoKey(block.id)), new ByteArrayWrapper(blockInfo.bytes))),
      new JArrayList()
    )
    activeChain.updateSemanticValidity(block.id, status)
    this
  }

  def updateBestBlock(block: SidechainBlock, blockInfo: SidechainBlockInfo): Try[SidechainHistoryStorage] = Try {
    storage.update(
      new ByteArrayWrapper(nextVersion),
      java.util.Arrays.asList(new JPair(bestBlockIdKey, new ByteArrayWrapper(idToBytes(block.id)))),
      new JArrayList()
    )
    activeChain.updateTip(block.id, blockInfo)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}

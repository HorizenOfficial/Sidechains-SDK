package com.horizen.storage

import java.util.{List => JList, ArrayList => JArrayList}

import com.google.common.primitives.Longs
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.util.{bytesToId, idToBytes}
import scorex.core.consensus.ModifierSemanticValidity
import scorex.crypto.hash.Blake2b256
import scorex.util.{ModifierId, ScorexLogging}
import javafx.util.{Pair => JPair}

import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Random, Try}

class SidechainHistoryStorage(storage : Storage, sidechainTransactionsCompanion: SidechainTransactionsCompanion, params: NetworkParams)
  extends ScorexLogging {
  // Version - RandomBytes(32)

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainTransactionsCompanion != null, "SidechainTransactionsCompanion must be NOT NULL.")

  private val bestBlockIdKey: ByteArrayWrapper = new ByteArrayWrapper(Array.fill(32)(-1: Byte))


  def height: Long = heightOf(bestBlockId).getOrElse(0L)

  def bestBlockId: ModifierId = storage.get(bestBlockIdKey).asScala.map(d => bytesToId(d.data)).getOrElse(bytesToId(params.sidechainGenesisBlockId))

  def bestBlock: SidechainBlock = {
    require(height > 0, "SidechainHistory is empty. Cannot retrieve best block.")
    blockById(bestBlockId).get
  }

  def blockById(blockId: ModifierId): Option[SidechainBlock] = {
    storage.get(new ByteArrayWrapper(idToBytes(blockId))).asScala.flatMap { baw =>
      val tryBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(baw.data)
      tryBlock match {
        case Failure(e) => log.warn("SidechainHistoryStorage: Failed to parse block bytes from storage.", e)
        case _ =>
      }
      tryBlock.toOption
    }
  }

  private def blockParentIdKey(blockId:ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"parentIdFor$blockId"))

  def parentBlockId(blockId: ModifierId): Option[ModifierId] = {
    storage.get(blockParentIdKey(blockId)).asScala match {
      case Some(baw) => Some(bytesToId(baw.data))
      case _ => None
    }
  }

  private def blockHeightKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"height$blockId"))

  def heightOf(blockId: ModifierId): Option[Long] =  storage.get(blockHeightKey(blockId)).asScala.map(b => BytesUtils.getLong(b.data, 0))

  private def chainScoreKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"chainScore$blockId"))

  def chainScoreFor(blockId: ModifierId): Option[Long] =  storage.get(chainScoreKey(blockId)).asScala.map(b => BytesUtils.getLong(b.data, 0))

  def update(block: SidechainBlock, chainScore: Long, isBest: Boolean): Try[SidechainHistoryStorage] = Try {
    require(block != null, "SidechainBlock must be NOT NULL.")

    val toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = new JArrayList()

    // add block height data
    toUpdate.add(new JPair(blockHeightKey(block.id), new ByteArrayWrapper(Longs.toByteArray(heightOf(block.parentId).getOrElse(0L) + 1))))

    // if best, add best block data
    if(isBest)
      toUpdate.add(new JPair(bestBlockIdKey, new ByteArrayWrapper(idToBytes(block.id))))

    // add chain new score
    toUpdate.add(new JPair(chainScoreKey(block.id), new ByteArrayWrapper(Longs.toByteArray(chainScore))))


    // add parent block Id. Used when we want to loop though chain ids without extracting and parsing the whole block
    toUpdate.add(new JPair(blockParentIdKey(block.id), new ByteArrayWrapper(idToBytes(block.parentId))))

    // add block
    toUpdate.add(new JPair(new ByteArrayWrapper(idToBytes(block.id)), new ByteArrayWrapper(block.bytes)))

    val version = new Array[Byte](32)
    Random.nextBytes(version)

    storage.update(
      new ByteArrayWrapper(version),
      toUpdate,
      new JArrayList[ByteArrayWrapper]())

    this
  }

  def semanticValidity(id: ModifierId): ModifierSemanticValidity = ???

  def updateSemanticValidity(block: SidechainBlock, status: ModifierSemanticValidity): Try[SidechainHistoryStorage] = ???
}

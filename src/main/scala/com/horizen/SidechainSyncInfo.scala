package com.horizen

import com.horizen.block.SidechainBlock
import scorex.core.NodeViewModifier
import scorex.core.consensus.History.ModifierIds
import scorex.util.ModifierId
import scorex.core.consensus.SyncInfo

import scorex.core.network.message.SyncInfoMessageSpec
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}
import scorex.util.{bytesToId, idToBytes}

import scala.util.Try

// knownBlockIds ordered backward most recent one to oldest
case class SidechainSyncInfo(knownBlockIds: Seq[ModifierId]) extends SyncInfo {
  override type M = SidechainSyncInfo

  override def serializer: ScorexSerializer[SidechainSyncInfo] = SidechainSyncInfoSerializer

  // get most recent block
  override def startingPoints: ModifierIds = {
    knownBlockIds.headOption match {
      case Some(id) => Seq(SidechainBlock.ModifierTypeId -> id)
      case None => Seq()
    }
  }
}


object SidechainSyncInfoSerializer extends ScorexSerializer[SidechainSyncInfo] {

  /*
  override def toBytes(obj: SidechainSyncInfo): Array[Byte] = {
    Array(obj.knownBlockIds.size.toByte) ++
    obj.knownBlockIds.foldLeft(Array[Byte]())((a, b) => a ++ idToBytes(b))
  }

  override def parseBytesTry(bytes: Array[Byte]): Try[SidechainSyncInfo] = Try {
    val length: Int = bytes.head & 0xFF
    if((bytes.length - 1) != length * NodeViewModifier.ModifierIdSize)
      throw new IllegalArgumentException("Input data corrupted.")
    var currentOffset: Int = 1

    var modifierIds: Seq[ModifierId] = Seq()

    while(currentOffset < bytes.length) {
      modifierIds = modifierIds :+ bytesToId(bytes.slice(currentOffset, currentOffset + NodeViewModifier.ModifierIdSize))
      currentOffset += NodeViewModifier.ModifierIdSize
    }

    SidechainSyncInfo(modifierIds)
  }
  */

  //TODO finish implementation
  override def serialize(obj: SidechainSyncInfo, w: Writer): Unit = {
    w.put((obj.knownBlockIds.size & 0xFF).toByte)
    for(b <- obj.knownBlockIds)
      w.putBytes(idToBytes(b))
  }

  override def parse(r: Reader): SidechainSyncInfo = {
    val length = r.getByte() & 0xFF
    if (r.remaining != length * NodeViewModifier.ModifierIdSize)
      throw new IllegalArgumentException("Input data corrupted.")

    val modifierIds : Seq[ModifierId] = for(b <- 0 until length)
      yield bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    SidechainSyncInfo(modifierIds)
  }
}

object SidechainSyncInfoMessageSpec extends SyncInfoMessageSpec[SidechainSyncInfo](SidechainSyncInfoSerializer)


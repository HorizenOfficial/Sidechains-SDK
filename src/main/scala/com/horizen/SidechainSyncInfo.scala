package com.horizen

import com.horizen.block.SidechainBlock
import com.horizen.utils.BytesUtils
import scorex.core.NodeViewModifier
import scorex.core.consensus.History.ModifierIds
import scorex.util.ModifierId
import scorex.core.consensus.SyncInfo
import scorex.core.serialization.Serializer
import scorex.util.{idToBytes, bytesToId}

import scala.util.Try

// knownBlockIds ordered backward most recent one to oldest
case class SidechainSyncInfo(knownBlockIds: Seq[ModifierId]) extends SyncInfo {
  override type M = SidechainSyncInfo

  override def serializer: Serializer[SidechainSyncInfo] = SidechainSyncInfoSerializer

  // get most recent block
  override def startingPoints: ModifierIds = {
    knownBlockIds.lastOption match {
      case Some(id) => Seq(SidechainBlock.ModifierTypeId -> id)
      case None => Seq()
    }
  }
}


object SidechainSyncInfoSerializer extends Serializer[SidechainSyncInfo] {
  override def toBytes(obj: SidechainSyncInfo): Array[Byte] = {
    Array(obj.knownBlockIds.size.toByte) ++
    obj.knownBlockIds.foldLeft(Array[Byte]())((a, b) => a ++ idToBytes(b))
  }

  override def parseBytes(bytes: Array[Byte]): Try[SidechainSyncInfo] = Try {
    val length: Int = bytes.head.toInt
    if((bytes.length - 1) != length * NodeViewModifier.ModifierIdSize)
      throw new IllegalArgumentException("Input data corrupted.")
    var currentOffset: Int = 4

    var modifierIds: Seq[ModifierId] = Seq()

    while(currentOffset < bytes.length) {
      modifierIds = modifierIds :+ bytesToId(BytesUtils.reverseBytes(bytes.slice(currentOffset, currentOffset + NodeViewModifier.ModifierIdSize)))
      currentOffset += NodeViewModifier.ModifierIdSize
    }

    SidechainSyncInfo(modifierIds)
  }

}
package com.horizen

import com.horizen.block.SidechainBlock
import sparkz.core.NodeViewModifier
import sparkz.core.consensus.History.ModifierIds
import scorex.util.ModifierId
import sparkz.core.consensus.SyncInfo

import sparkz.core.network.message.SyncInfoMessageSpec
import sparkz.core.serialization.SparkzSerializer
import scorex.util.serialization.{Reader, Writer}
import scorex.util.{bytesToId, idToBytes}


// knownBlockIds ordered backward most recent one to oldest
case class SidechainSyncInfo(knownBlockIds: Seq[ModifierId]) extends SyncInfo {
  override type M = SidechainSyncInfo

  override def serializer: SparkzSerializer[SidechainSyncInfo] = SidechainSyncInfoSerializer

  // get most recent block
  override def startingPoints: ModifierIds = {
    knownBlockIds.headOption match {
      case Some(id) => Seq(SidechainBlock.ModifierTypeId -> id)
      case None => Seq()
    }
  }
}


object SidechainSyncInfoSerializer extends SparkzSerializer[SidechainSyncInfo] {

  override def serialize(obj: SidechainSyncInfo, w: Writer): Unit = {
    w.putInt(obj.knownBlockIds.size)
    for(b <- obj.knownBlockIds)
      w.putBytes(idToBytes(b))
  }

  override def parse(r: Reader): SidechainSyncInfo = {
    val length = r.getInt()
    if (r.remaining < length * NodeViewModifier.ModifierIdSize)
      throw new IllegalArgumentException("Input data corrupted.")

    val modifierIds : Seq[ModifierId] = for(b <- 0 until length)
      yield bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    SidechainSyncInfo(modifierIds)
  }
}

object SidechainSyncInfoMessageSpec extends SyncInfoMessageSpec[SidechainSyncInfo](SidechainSyncInfoSerializer)


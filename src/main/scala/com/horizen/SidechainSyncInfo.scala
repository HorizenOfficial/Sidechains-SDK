package com.horizen

import scorex.core.consensus.SyncInfo
import scorex.core.network.message.SyncInfoMessageSpec
import scorex.core.{ModifierId, ModifierTypeId, NodeViewModifier, bytesToId, idToBytes}
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}

import scala.util.Try

class SidechainSyncInfo
  extends SyncInfo
{
  override def startingPoints: Seq[(ModifierTypeId, ModifierId)] = ???

  override type M = SidechainSyncInfo

  override def serializer: ScorexSerializer[SidechainSyncInfo] = SidechainSyncInfoSerializer

}

object SidechainSyncInfoSerializer extends ScorexSerializer[SidechainSyncInfo] {

  override def toBytes(obj: SidechainSyncInfo): Array[Byte] =
    Array.emptyByteArray

  override def parseBytesTry(bytes: Array[Byte]): Try[SidechainSyncInfo] = Try {
    new SidechainSyncInfo()
  }

  override def serialize(obj: SidechainSyncInfo, w: Writer): Unit = ???

  override def parse(r: Reader): SidechainSyncInfo = ???
}

object SidechainSyncInfoMessageSpec extends SyncInfoMessageSpec[SidechainSyncInfo](SidechainSyncInfoSerializer)
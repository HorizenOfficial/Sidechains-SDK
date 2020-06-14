package com.horizen.consensus

import scorex.core.NodeViewModifier
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class ForgingStakeInfo(boxId: Array[Byte], value: Long) extends BytesSerializable {
  require(boxId.length == NodeViewModifier.ModifierIdSize, "BoxId length is wrong.")
  require(value >= 0, "Value expected to be non negative.")

  override type M = ForgingStakeInfo

  override def serializer: ScorexSerializer[ForgingStakeInfo] = ForgingStakeInfoSerializer

  override def equals(obj: Any): Boolean = {
    obj match {
      case info: ForgingStakeInfo => boxId.sameElements(info.boxId) && value == info.value
      case _ => false
    }
  }
}

object ForgingStakeInfoSerializer extends ScorexSerializer[ForgingStakeInfo]{
  override def serialize(obj: ForgingStakeInfo, w: Writer): Unit = {
    w.putBytes(obj.boxId)
    w.putLong(obj.value)
  }

  override def parse(r: Reader): ForgingStakeInfo = {
    val boxId = r.getBytes(NodeViewModifier.ModifierIdSize)
    val value = r.getLong()
    ForgingStakeInfo(boxId, value)
  }
}
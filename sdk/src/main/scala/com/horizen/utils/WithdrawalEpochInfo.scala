package com.horizen.utils

import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}


case class WithdrawalEpochInfo(epoch: Int, // epoch number, SidechainBlock belongs to. Counted in MC Blocks.
                               index: Int  // position of SidechainBlock in the epoch. Equals to the most recent MC block reference position in current withdrawal epoch.
                              ) extends BytesSerializable {
  override type M = WithdrawalEpochInfo

  override def serializer: ScorexSerializer[WithdrawalEpochInfo] = WithdrawalEpochInfoSerializer
}


object WithdrawalEpochInfoSerializer extends ScorexSerializer[WithdrawalEpochInfo] {
  override def serialize(obj: WithdrawalEpochInfo, w: Writer): Unit = {
    w.putInt(obj.epoch)
    w.putInt(obj.index)
  }

  override def parse(r: Reader): WithdrawalEpochInfo = {
    val epoch = r.getInt()
    val index = r.getInt()
    WithdrawalEpochInfo(epoch, index)
  }
}

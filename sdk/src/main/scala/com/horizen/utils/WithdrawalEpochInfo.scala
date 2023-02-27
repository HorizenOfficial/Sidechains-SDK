package com.horizen.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.json.Views
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}


@JsonView(Array(classOf[Views.Default]))
case class WithdrawalEpochInfo(epoch: Int, // epoch number, SidechainBlock belongs to. Counted in MC Blocks.
                               lastEpochIndex: Int // position of SidechainBlock in the epoch. Equals to the most recent MC block reference position in current withdrawal epoch.
                              ) extends BytesSerializable {
  override type M = WithdrawalEpochInfo

  override def serializer: SparkzSerializer[WithdrawalEpochInfo] = WithdrawalEpochInfoSerializer
}


object WithdrawalEpochInfoSerializer extends SparkzSerializer[WithdrawalEpochInfo] {
  override def serialize(obj: WithdrawalEpochInfo, w: Writer): Unit = {
    w.putInt(obj.epoch)
    w.putInt(obj.lastEpochIndex)
  }

  override def parse(r: Reader): WithdrawalEpochInfo = {
    val epoch = r.getInt()
    val lastEpochIndex = r.getInt()
    WithdrawalEpochInfo(epoch, lastEpochIndex)
  }
}

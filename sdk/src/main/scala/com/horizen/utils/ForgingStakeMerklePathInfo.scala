package com.horizen.utils

import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}


case class ForgingStakeMerklePathInfo(forgingStakeInfo: ForgingStakeInfo, merklePath: MerklePath) extends BytesSerializable {

  override type M = ForgingStakeMerklePathInfo

  override def serializer: ScorexSerializer[ForgingStakeMerklePathInfo] = ForgerBoxMerklePathInfoSerializer
}

object ForgerBoxMerklePathInfoSerializer extends ScorexSerializer[ForgingStakeMerklePathInfo] {
  override def serialize(obj: ForgingStakeMerklePathInfo, w: Writer): Unit = {
    val forgingStakeInfoBytes = ForgingStakeInfoSerializer.toBytes(obj.forgingStakeInfo)
    w.putInt(forgingStakeInfoBytes.length)
    w.putBytes(forgingStakeInfoBytes)
    val merklePathBytes = obj.merklePath.bytes()
    w.putInt(merklePathBytes.length)
    w.putBytes(merklePathBytes)
  }

  override def parse(r: Reader): ForgingStakeMerklePathInfo = {
    val forgingStakeInfoBytes = r.getInt()
    val forgingStakeInfo = ForgingStakeInfoSerializer.parseBytes(r.getBytes(forgingStakeInfoBytes))

    val merklePathBytesLength = r.getInt()
    val merklePath = MerklePath.parseBytes(r.getBytes(merklePathBytesLength))
    ForgingStakeMerklePathInfo(forgingStakeInfo, merklePath)
  }
}
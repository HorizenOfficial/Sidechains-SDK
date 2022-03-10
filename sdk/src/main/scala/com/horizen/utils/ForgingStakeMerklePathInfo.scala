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
    ForgingStakeInfoSerializer.serialize(obj.forgingStakeInfo, w)
    MerklePathSerializer.getSerializer.serialize(obj.merklePath, w)
  }

  override def parse(r: Reader): ForgingStakeMerklePathInfo = {
    val forgingStakeInfo = ForgingStakeInfoSerializer.parse(r)
    val merklePath = MerklePathSerializer.getSerializer.parse(r)
    ForgingStakeMerklePathInfo(forgingStakeInfo, merklePath)
  }
}
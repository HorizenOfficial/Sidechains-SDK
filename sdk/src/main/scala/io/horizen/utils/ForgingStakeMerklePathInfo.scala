package io.horizen.utils

import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}


case class ForgingStakeMerklePathInfo(forgingStakeInfo: ForgingStakeInfo, merklePath: MerklePath) extends BytesSerializable {

  override type M = ForgingStakeMerklePathInfo

  override def serializer: SparkzSerializer[ForgingStakeMerklePathInfo] = ForgerBoxMerklePathInfoSerializer
}

object ForgerBoxMerklePathInfoSerializer extends SparkzSerializer[ForgingStakeMerklePathInfo] {
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
package com.horizen.utils

import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class BlockFeeInfo(fee: Long,
                        forgerRewardKey: PublicKey25519Proposition
                       ) extends BytesSerializable {
  override type M = BlockFeeInfo

  override def serializer: ScorexSerializer[BlockFeeInfo] = BlockFeeInfoSerializer
}


object BlockFeeInfoSerializer extends ScorexSerializer[BlockFeeInfo] {
  override def serialize(obj: BlockFeeInfo, w: Writer): Unit = {
    w.putLong(obj.fee)
    PublicKey25519PropositionSerializer.getSerializer.serialize(obj.forgerRewardKey, w)
  }

  override def parse(r: Reader): BlockFeeInfo = {
    val fee: Long = r.getLong()
    val forgerRewardKey: PublicKey25519Proposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)

    BlockFeeInfo(fee, forgerRewardKey)
  }
}

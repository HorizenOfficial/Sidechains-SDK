package io.horizen.utxo.utils

import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class BlockFeeInfo(fee: Long,
                        forgerRewardKey: PublicKey25519Proposition
                       ) extends BytesSerializable {
  override type M = BlockFeeInfo

  override def serializer: SparkzSerializer[BlockFeeInfo] = BlockFeeInfoSerializer
}


object BlockFeeInfoSerializer extends SparkzSerializer[BlockFeeInfo] {
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

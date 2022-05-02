package com.horizen.consensus

import com.fasterxml.jackson.annotation.JsonView
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.box.ForgerBox
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.serialization.Views
import com.horizen.utils.{ByteArrayWrapper, Utils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

@JsonView(Array(classOf[Views.Default]))
case class ForgingStakeInfo(blockSignPublicKey: PublicKey25519Proposition,
                            vrfPublicKey: VrfPublicKey,
                            stakeAmount: Long) extends BytesSerializable with Ordered[ForgingStakeInfo] {
  require(stakeAmount >= 0, "stakeAmount expected to be non negative.")

  override type M = ForgingStakeInfo

  override def serializer: ScorexSerializer[ForgingStakeInfo] = ForgingStakeInfoSerializer

  def hash: Array[Byte] = Utils.doubleSHA256Hash(
    Bytes.concat(blockSignPublicKey.bytes(), vrfPublicKey.bytes(), Longs.toByteArray(stakeAmount))
  )

  override def toString: String = "%s(blockSignPublicKey: %s, vrfPublicKey: %s, stakeAmount: %d)"
    .format(this.getClass.toString, blockSignPublicKey, vrfPublicKey, stakeAmount)

  override def compare(that: ForgingStakeInfo): Int = {
    // Compare by stake
    stakeAmount.compareTo(that.stakeAmount) match {
        // if equals -> compare by blockSignPublicKey
      case 0 => ByteArrayWrapper.compare(blockSignPublicKey.pubKeyBytes(), that.blockSignPublicKey.pubKeyBytes()) match {
        // if equals -> compare by vrfPublicKey
        case 0 => ByteArrayWrapper.compare(vrfPublicKey.pubKeyBytes(), that.vrfPublicKey.pubKeyBytes())
        case diff => diff
      }
      case diff => diff
    }
  }
}

object ForgingStakeInfo {
  def fromForgerBoxes(forgerBoxes: Seq[ForgerBox]): Seq[ForgingStakeInfo] = {
    forgerBoxes.view
      .groupBy(box => (box.blockSignProposition(), box.vrfPubKey()))
      .map { case ((blockSignKey, vrfKey), boxes) => ForgingStakeInfo(blockSignKey, vrfKey, boxes.map(_.value()).sum) }
      .toSeq
  }
}

object ForgingStakeInfoSerializer extends ScorexSerializer[ForgingStakeInfo]{
  override def serialize(obj: ForgingStakeInfo, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(obj.blockSignPublicKey, w)
    VrfPublicKeySerializer.getSerializer.serialize(obj.vrfPublicKey, w)
    w.putLong(obj.stakeAmount)
  }

  override def parse(r: Reader): ForgingStakeInfo = {
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val stakeAmount = r.getLong()

    ForgingStakeInfo(blockSignPublicKey, vrfPublicKey, stakeAmount)
  }
}

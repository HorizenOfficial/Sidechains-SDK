package com.horizen.consensus

import com.fasterxml.jackson.annotation.JsonView
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.box.ForgerBox
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.serialization.Views
import com.horizen.utils.Utils
import io.iohk.iodb.ByteArrayWrapper
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
      case 0 => ByteArrayWrapper(blockSignPublicKey.pubKeyBytes()).compare(ByteArrayWrapper(that.blockSignPublicKey.pubKeyBytes())) match {
        // if equals -> compare by vrfPublicKey
        case 0 => ByteArrayWrapper(vrfPublicKey.pubKeyBytes()).compare(ByteArrayWrapper(that.vrfPublicKey.pubKeyBytes()))
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
    val blockSignPublicKeyBytes = PublicKey25519PropositionSerializer.getSerializer.toBytes(obj.blockSignPublicKey)
    w.putInt(blockSignPublicKeyBytes.length)
    w.putBytes(blockSignPublicKeyBytes)

    val vrfPublicKeyBytes = VrfPublicKeySerializer.getSerializer.toBytes(obj.vrfPublicKey)
    w.putInt(vrfPublicKeyBytes.length)
    w.putBytes(vrfPublicKeyBytes)

    w.putLong(obj.stakeAmount)
  }

  override def parse(r: Reader): ForgingStakeInfo = {
    val blockSignPublicKeyBytesLength = r.getInt()
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parseBytes(r.getBytes(blockSignPublicKeyBytesLength))

    val vrfPublicKeyBytesLength = r.getInt()
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parseBytes(r.getBytes(vrfPublicKeyBytesLength))

    val stakeAmount = r.getLong()

    ForgingStakeInfo(blockSignPublicKey, vrfPublicKey, stakeAmount)
  }
}
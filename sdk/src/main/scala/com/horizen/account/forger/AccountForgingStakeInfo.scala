package com.horizen.account.forger

import com.fasterxml.jackson.annotation.JsonView
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.box.ForgerBox
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.serialization.Views
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, Utils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

@JsonView(Array(classOf[Views.Default]))
case class AccountForgingStakeInfo(
                          stakeId: Array[Byte],
                          blockSignProposition: PublicKey25519Proposition,
                          vrfPublicKey: VrfPublicKey,
                          ownerPublicKey: AddressProposition,
                          stakedAmount: Long,
                          consensusEpochNumber: Int
                          )
  extends BytesSerializable  {
  require(stakedAmount >= 0, "stakeAmount expected to be non negative.")

  override type M = AccountForgingStakeInfo

  override def serializer: ScorexSerializer[AccountForgingStakeInfo] = AccountForgingStakeInfoSerializer

  override def toString: String = "%s(stakeId: %s, blockSignPublicKey: %s, vrfPublicKey: %s, ownerPublicKey: %s, stakeAmount: %d, epoch: %d)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), blockSignProposition, vrfPublicKey, ownerPublicKey, stakedAmount, consensusEpochNumber)

}


object AccountForgingStakeInfoSerializer extends ScorexSerializer[AccountForgingStakeInfo]{
  override def serialize(s: AccountForgingStakeInfo, w: Writer): Unit = {
    w.putBytes(s.stakeId)
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putLong(s.stakedAmount)
    w.putInt(s.consensusEpochNumber)
  }

  override def parse(r: Reader): AccountForgingStakeInfo = {
    val stakeId = r.getBytes(32)
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmount = r.getLong()
    val consensusEpochNumber = r.getInt

    AccountForgingStakeInfo(
      stakeId, blockSignPublicKey, vrfPublicKey, ownerPublicKey, stakeAmount, consensusEpochNumber)

  }
}

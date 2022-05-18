package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, Signature25519Serializer, VrfProof, VrfProofSerializer}
import com.horizen.serialization.{MerklePathJsonSerializer, ScorexModifierIdSerializer, Views}
import com.horizen.utils.{MerklePath, MerklePathSerializer}
import com.horizen.validation.{InvalidSidechainBlockHeaderException}
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import scorex.core.block.Block
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.core.{NodeViewModifier, bytesToId, idToBytes}
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}

import scala.util.Try

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "serializer"))
case class SidechainAccountBlockHeader(
                                        override val version: Block.Version,
                                        @JsonSerialize(using = classOf[ScorexModifierIdSerializer])override val parentId: ModifierId,
                                        override val timestamp: Block.Timestamp,
                                        override val forgingStakeInfo: ForgingStakeInfo,
                                        @JsonSerialize(using = classOf[MerklePathJsonSerializer])override val forgingStakeMerklePath: MerklePath,
                                        override val vrfProof: VrfProof,
                                        override val sidechainTransactionsMerkleRootHash: Array[Byte], // don't need to care about MC2SCAggTxs here
                                        override val mainchainMerkleRootHash: Array[Byte], // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders root hash
                                        stateRoot: Array[Byte],
                                        receiptsRoot: Array[Byte],
                                        override val ommersMerkleRootHash: Array[Byte], // build on top of Ommer.id()
                                        override val ommersCumulativeScore: Long, // to be able to calculate the score of the block without having the full SB. For future
                                        override val feePaymentsHash: Array[Byte], // hash of the fee payments created during applying this block to the state. zeros by default.
                                        override val signature: Signature25519
                               ) extends SidechainBlockHeaderBase with BytesSerializable {

  override type M = SidechainAccountBlockHeader

  override def serializer: ScorexSerializer[SidechainAccountBlockHeader] = SidechainAccountBlockHeaderSerializer

  @JsonSerialize(using = classOf[ScorexModifierIdSerializer])
  override lazy val id: ModifierId = bytesToId(Blake2b256(Bytes.concat(messageToSign, signature.bytes)))

  override lazy val messageToSign: Array[Byte] = {
    Bytes.concat(
      Array[Byte]{version},
      idToBytes(parentId),
      Longs.toByteArray(timestamp),
      forgingStakeInfo.hash,
      vrfProof.bytes, // TO DO: is it ok or define vrfProof.id() ?
      forgingStakeMerklePath.bytes(),
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      stateRoot,
      receiptsRoot,
      ommersMerkleRootHash,
      Longs.toByteArray(ommersCumulativeScore),
      feePaymentsHash
    )
  }

  override def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    if(parentId.length != 64
      || sidechainTransactionsMerkleRootHash.length != 32
      || mainchainMerkleRootHash.length != 32
      || stateRoot.length != 32
      || receiptsRoot.length != 32
      || ommersMerkleRootHash.length != 32
      || ommersCumulativeScore < 0
      || feePaymentsHash.length != 32
      || timestamp <= 0)
      throw new InvalidSidechainBlockHeaderException(s"SidechainAccountBlockHeader $id contains out of bound fields.")

    if(version != SidechainAccountBlock.BLOCK_VERSION)
      throw new InvalidSidechainBlockHeaderException(s"SidechainAccountBlock $id version $version is invalid.")

    // check, that signature is valid
    if(!signature.isValid(forgingStakeInfo.blockSignPublicKey, messageToSign))
      throw new InvalidSidechainBlockHeaderException(s"SidechainAccountBlockHeader $id signature is invalid.")
  }


  override def toString =
    s"SidechainAccountBlockHeader($id, $version, $timestamp, $forgingStakeInfo, $vrfProof, " +
      s"${ByteUtils.toHexString(sidechainTransactionsMerkleRootHash)}, ${ByteUtils.toHexString(mainchainMerkleRootHash)}, " +
      s"${ByteUtils.toHexString(stateRoot)}, ${ByteUtils.toHexString(receiptsRoot)}" +
      s"${ByteUtils.toHexString(ommersMerkleRootHash)}, $ommersCumulativeScore, $signature)"
}


object SidechainAccountBlockHeaderSerializer extends ScorexSerializer[SidechainAccountBlockHeader] {
  override def serialize(obj: SidechainAccountBlockHeader, w: Writer): Unit = {
    w.put(obj.version)

    w.putBytes(idToBytes(obj.parentId))

    w.putLong(obj.timestamp)

    ForgingStakeInfoSerializer.serialize(obj.forgingStakeInfo, w)

    MerklePathSerializer.getSerializer.serialize(obj.forgingStakeMerklePath, w)

    VrfProofSerializer.getSerializer.serialize(obj.vrfProof, w)

    w.putBytes(obj.sidechainTransactionsMerkleRootHash)

    w.putBytes(obj.mainchainMerkleRootHash)

    w.putBytes(obj.stateRoot)

    w.putBytes(obj.receiptsRoot)

    w.putBytes(obj.ommersMerkleRootHash)

    w.putLong(obj.ommersCumulativeScore)

    w.putBytes(obj.feePaymentsHash)

    Signature25519Serializer.getSerializer.serialize(obj.signature, w)
  }

  override def parse(r: Reader): SidechainAccountBlockHeader = {
    val version: Block.Version = r.getByte()

    if(version != SidechainAccountBlock.BLOCK_VERSION)
      throw new InvalidSidechainBlockHeaderException(s"SidechainAccountBlock version $version is invalid.")

    val parentId: ModifierId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    val timestamp: Block.Timestamp = r.getLong()

    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfoSerializer.parse(r)

    val forgingStakeMerkle: MerklePath = MerklePathSerializer.getSerializer.parse(r)

    val vrfProof: VrfProof = VrfProofSerializer.getSerializer.parse(r)

    val sidechainTransactionsMerkleRootHash = r.getBytes(NodeViewModifier.ModifierIdSize)

    val mainchainMerkleRootHash = r.getBytes(NodeViewModifier.ModifierIdSize)

    val stateRoot = r.getBytes(NodeViewModifier.ModifierIdSize)

    val receiptsRoot = r.getBytes(NodeViewModifier.ModifierIdSize)

    val ommersMerkleRootHash = r.getBytes(NodeViewModifier.ModifierIdSize)

    val ommersCumulativeScore: Long = r.getLong()

    val feePaymentsHash: Array[Byte] = r.getBytes(NodeViewModifier.ModifierIdSize)

    val signature: Signature25519 = Signature25519Serializer.getSerializer.parse(r)

    SidechainAccountBlockHeader(
      version,
      parentId,
      timestamp,
      forgingStakeInfo,
      forgingStakeMerkle,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      stateRoot,
      receiptsRoot,
      ommersMerkleRootHash,
      ommersCumulativeScore,
      feePaymentsHash,
      signature)
  }
}
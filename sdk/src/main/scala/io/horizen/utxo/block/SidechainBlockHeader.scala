package io.horizen.utxo.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Longs}
import io.horizen.block.SidechainBlockHeaderBase
import io.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import io.horizen.history.validation.InvalidSidechainBlockHeaderException
import io.horizen.json.serializer.{MerklePathJsonSerializer, SparkzModifierIdSerializer}
import io.horizen.params.NetworkParams
import io.horizen.proof.{Signature25519, Signature25519Serializer, VrfProof, VrfProofSerializer}
import io.horizen.json.Views
import io.horizen.utils.{BytesUtils, MerklePath, MerklePathSerializer, MerkleTree}
import sparkz.core.block.Block
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.core.{NodeViewModifier, bytesToId, idToBytes}
import sparkz.util.ModifierId
import sparkz.util.serialization.{Reader, Writer}

import scala.util.{Failure, Success, Try}

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "serializer"))
case class SidechainBlockHeader(
                                 override val version: Block.Version,
                                 @JsonSerialize(using = classOf[SparkzModifierIdSerializer])override val parentId: ModifierId,
                                 override val timestamp: Block.Timestamp,
                                 override val forgingStakeInfo: ForgingStakeInfo,
                                 @JsonSerialize(using = classOf[MerklePathJsonSerializer])override val forgingStakeMerklePath: MerklePath,
                                 override val vrfProof: VrfProof,
                                 override val sidechainTransactionsMerkleRootHash: Array[Byte], // don't need to care about MC2SCAggTxs here
                                 override val mainchainMerkleRootHash: Array[Byte], // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders root hash
                                 override val ommersMerkleRootHash: Array[Byte], // build on top of Ommer.id()
                                 override val ommersCumulativeScore: Long, // to be able to calculate the score of the block without having the full SB. For future
                                 override val feePaymentsHash: Array[Byte], // hash of the fee payments created during applying this block to the state. zeros by default.
                                 override val signature: Signature25519
                               ) extends SidechainBlockHeaderBase with BytesSerializable {

  override type M = SidechainBlockHeader

  override def serializer: SparkzSerializer[SidechainBlockHeader] = SidechainBlockHeaderSerializer

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
      ommersMerkleRootHash,
      Longs.toByteArray(ommersCumulativeScore),
      feePaymentsHash
    )
  }

  override def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    super.semanticValidity(params) match {
      case Success(_) =>

        if (version != SidechainBlock.BLOCK_VERSION)
          throw new InvalidSidechainBlockHeaderException(s"SidechainBlock $id version $version is invalid.")

        // check, that signature is valid
        if (!signature.isValid(forgingStakeInfo.blockSignPublicKey, messageToSign))
          throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeader $id signature is invalid.")

      case Failure(exception) =>
        throw exception
    }
  }


  override def toString =
    s"SidechainBlockHeader($id, $version, $timestamp, $forgingStakeInfo, $vrfProof, ${BytesUtils.toHexString(sidechainTransactionsMerkleRootHash)}, ${BytesUtils.toHexString(mainchainMerkleRootHash)}, ${BytesUtils.toHexString(ommersMerkleRootHash)}, $ommersCumulativeScore, $signature)"
}


object SidechainBlockHeaderSerializer extends SparkzSerializer[SidechainBlockHeader] {
  override def serialize(obj: SidechainBlockHeader, w: Writer): Unit = {
    w.put(obj.version)

    w.putBytes(idToBytes(obj.parentId))

    w.putLong(obj.timestamp)

    ForgingStakeInfoSerializer.serialize(obj.forgingStakeInfo, w)

    MerklePathSerializer.getSerializer.serialize(obj.forgingStakeMerklePath, w)

    VrfProofSerializer.getSerializer.serialize(obj.vrfProof, w)

    w.putBytes(obj.sidechainTransactionsMerkleRootHash)

    w.putBytes(obj.mainchainMerkleRootHash)

    w.putBytes(obj.ommersMerkleRootHash)

    w.putLong(obj.ommersCumulativeScore)

    w.putBytes(obj.feePaymentsHash)

    Signature25519Serializer.getSerializer.serialize(obj.signature, w)
  }

  override def parse(r: Reader): SidechainBlockHeader = {
    val version: Block.Version = r.getByte()

    if(version != SidechainBlock.BLOCK_VERSION)
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlock version $version is invalid.")

    val parentId: ModifierId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    val timestamp: Block.Timestamp = r.getLong()

    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfoSerializer.parse(r)

    val forgingStakeMerkle: MerklePath = MerklePathSerializer.getSerializer.parse(r)

    val vrfProof: VrfProof = VrfProofSerializer.getSerializer.parse(r)

    val sidechainTransactionsMerkleRootHash = r.getBytes(MerkleTree.ROOT_HASH_LENGTH)

    val mainchainMerkleRootHash = r.getBytes(MerkleTree.ROOT_HASH_LENGTH)

    val ommersMerkleRootHash = r.getBytes(MerkleTree.ROOT_HASH_LENGTH)

    val ommersCumulativeScore: Long = r.getLong()

    val feePaymentsHash: Array[Byte] = r.getBytes(NodeViewModifier.ModifierIdSize)

    val signature: Signature25519 = Signature25519Serializer.getSerializer.parse(r)

    SidechainBlockHeader(
      version,
      parentId,
      timestamp,
      forgingStakeInfo,
      forgingStakeMerkle,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommersCumulativeScore,
      feePaymentsHash,
      signature)
  }
}

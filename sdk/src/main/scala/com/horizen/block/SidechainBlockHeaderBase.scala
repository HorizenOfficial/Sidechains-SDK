package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, Signature25519Serializer, VrfProof, VrfProofSerializer}
import com.horizen.serialization.{MerklePathJsonSerializer, ScorexModifierIdSerializer, Views}
import com.horizen.utils.{MerklePath, MerklePathSerializer}
import com.horizen.validation.InvalidSidechainBlockHeaderException
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
abstract class SidechainBlockHeaderBase() {

  val version: Block.Version
  //@JsonSerialize(using = classOf[ScorexModifierIdSerializer]) val parentId: ModifierId
  val parentId: ModifierId
  val timestamp: Block.Timestamp
  val forgingStakeInfo: ForgingStakeInfo
  //@JsonSerialize(using = classOf[MerklePathJsonSerializer]) val forgingStakeMerklePath: MerklePath,
  val forgingStakeMerklePath: MerklePath
  val vrfProof: VrfProof
  val sidechainTransactionsMerkleRootHash: Array[Byte] // don't need to care about MC2SCAggTxs here
  val mainchainMerkleRootHash: Array[Byte] // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders root hash
  val ommersMerkleRootHash: Array[Byte] // build on top of Ommer.id()
  val ommersCumulativeScore: Long // to be able to calculate the score of the block without having the full SB. For future
  val feePaymentsHash: Array[Byte] // hash of the fee payments created during applying this block to the state. zeros by default.
  val signature: Signature25519


  @JsonSerialize(using = classOf[ScorexModifierIdSerializer])
  lazy val id: ModifierId = bytesToId(Blake2b256(Bytes.concat(messageToSign, signature.bytes)))

  lazy val messageToSign: Array[Byte] = {
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

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    if(parentId.length != 64
      || sidechainTransactionsMerkleRootHash.length != 32
      || mainchainMerkleRootHash.length != 32
      || ommersMerkleRootHash.length != 32
      || ommersCumulativeScore < 0
      || feePaymentsHash.length != 32
      || timestamp <= 0)
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeaderBase $id contains out of bound fields.")

    if(version != SidechainBlock.BLOCK_VERSION)
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlock $id version $version is invalid.")

    // check, that signature is valid
    if(!signature.isValid(forgingStakeInfo.blockSignPublicKey, messageToSign))
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeaderBase $id signature is invalid.")
  }


  override def toString =
    s"SidechainBlockHeaderBase($id, $version, $timestamp, $forgingStakeInfo, $vrfProof, ${ByteUtils.toHexString(sidechainTransactionsMerkleRootHash)}, ${ByteUtils.toHexString(mainchainMerkleRootHash)}, ${ByteUtils.toHexString(ommersMerkleRootHash)}, $ommersCumulativeScore, $signature)"
}



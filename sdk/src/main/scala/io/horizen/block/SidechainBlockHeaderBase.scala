package io.horizen.block

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.Bytes
import io.horizen.consensus.ForgingStakeInfo
import io.horizen.history.validation.InvalidSidechainBlockHeaderException
import io.horizen.json.serializer.SparkzModifierIdSerializer
import io.horizen.params.NetworkParams
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.utils.{MerklePath, MerkleTree}
import sparkz.core.block.Block
import sparkz.core.{NodeViewModifier, bytesToId}
import sparkz.crypto.hash.Blake2b256
import sparkz.util.ModifierId

import scala.util.Try


trait SidechainBlockHeaderBase {
  val version: Block.Version
  val parentId: ModifierId
  val timestamp: Block.Timestamp
  val forgingStakeInfo: ForgingStakeInfo
  val forgingStakeMerklePath: MerklePath
  val vrfProof: VrfProof
  val sidechainTransactionsMerkleRootHash: Array[Byte] // don't need to care about MC2SCAggTxs here
  val mainchainMerkleRootHash: Array[Byte] // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders root hash
  val ommersMerkleRootHash: Array[Byte] // build on top of Ommer.id()
  val ommersCumulativeScore: Long // to be able to calculate the score of the block without having the full SB. For future
  val feePaymentsHash: Array[Byte] // hash of the fee payments created during applying this block to the state. zeros by default.
  val signature: Signature25519


  @JsonSerialize(using = classOf[SparkzModifierIdSerializer])
  lazy val id: ModifierId = bytesToId(Blake2b256(Bytes.concat(messageToSign, signature.bytes)))

  val messageToSign: Array[Byte]

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    if(parentId.length != 64
      || sidechainTransactionsMerkleRootHash.length != MerkleTree.ROOT_HASH_LENGTH
      || mainchainMerkleRootHash.length != MerkleTree.ROOT_HASH_LENGTH
      || ommersMerkleRootHash.length != MerkleTree.ROOT_HASH_LENGTH
      || ommersCumulativeScore < 0
      || feePaymentsHash.length != NodeViewModifier.ModifierIdSize
      || timestamp <= 0)
      throw new InvalidSidechainBlockHeaderException(s"${getClass.getSimpleName} $id contains out of bound fields.")
  }
}

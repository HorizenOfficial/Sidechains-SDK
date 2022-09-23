package com.horizen.block

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.Bytes
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.serialization.ScorexModifierIdSerializer
import com.horizen.utils.MerklePath
import com.horizen.validation.InvalidSidechainBlockHeaderException
import sparkz.core.block.Block
import sparkz.core.bytesToId
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId

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


  @JsonSerialize(using = classOf[ScorexModifierIdSerializer])
  lazy val id: ModifierId = bytesToId(Blake2b256(Bytes.concat(messageToSign, signature.bytes)))

  val messageToSign: Array[Byte]

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    if(parentId.length != 64
      || sidechainTransactionsMerkleRootHash.length != 32
      || mainchainMerkleRootHash.length != 32
      || ommersMerkleRootHash.length != 32
      || ommersCumulativeScore < 0
      || feePaymentsHash.length != 32
      || timestamp <= 0)
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeaderBase $id contains out of bound fields.")
  }
}

package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.box.{ForgerBox, ForgerBoxSerializer}
import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, Signature25519Serializer, VrfProof}
import com.horizen.serialization.{ScorexModifierIdSerializer, Views}
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
case class SidechainBlockHeader(
                                 version: Block.Version,
                                 @JsonSerialize(using = classOf[ScorexModifierIdSerializer]) parentId: ModifierId,
                                 timestamp: Block.Timestamp,
                                 forgingStakeInfo: ForgingStakeInfo,
                                 @JsonSerialize(using = classOf[MerklePathSerializer]) forgingStakeMerklePath: MerklePath,
                                 vrfProof: VrfProof,
                                 sidechainTransactionsMerkleRootHash: Array[Byte], // don't need to care about MC2SCAggTxs here
                                 mainchainMerkleRootHash: Array[Byte], // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders root hash
                                 ommersMerkleRootHash: Array[Byte], // build on top of Ommer.id()
                                 ommersCumulativeScore: Long, // to be able to calculate the score of the block without having the full SB. For future
                                 signature: Signature25519
                               ) extends BytesSerializable {

  override type M = SidechainBlockHeader

  override def serializer: ScorexSerializer[SidechainBlockHeader] = SidechainBlockHeaderSerializer

  @JsonSerialize(using = classOf[ScorexModifierIdSerializer])
  lazy val id: ModifierId = bytesToId(Blake2b256(Bytes.concat(messageToSign, signature.bytes)))

  lazy val messageToSign: Array[Byte] = {
    Bytes.concat(
      idToBytes(parentId),
      Longs.toByteArray(timestamp),
      forgingStakeInfo.hash,
      vrfProof.bytes, // TO DO: is it ok or define vrfProof.id() ?
      forgingStakeMerklePath.bytes(),
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      Longs.toByteArray(ommersCumulativeScore)
    )
  }

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    if(parentId.length != 64
      || sidechainTransactionsMerkleRootHash.length != 32
      || mainchainMerkleRootHash.length != 32
      || ommersMerkleRootHash.length != 32
      || ommersCumulativeScore < 0
      || timestamp <= 0)
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeader $id contains out of bound fields.")

    if(version != SidechainBlock.BLOCK_VERSION)
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlock $id version $version is invalid.")
    // check, that signature is valid
    if(!signature.isValid(forgingStakeInfo.blockSignPublicKey, messageToSign))
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeader $id signature is invalid.")
  }


  override def toString =
    s"SidechainBlockHeader($id, $version, $timestamp, $forgingStakeInfo, $vrfProof, ${ByteUtils.toHexString(sidechainTransactionsMerkleRootHash)}, ${ByteUtils.toHexString(mainchainMerkleRootHash)}, ${ByteUtils.toHexString(ommersMerkleRootHash)}, $ommersCumulativeScore, $signature)"
}


object SidechainBlockHeaderSerializer extends ScorexSerializer[SidechainBlockHeader] {
  override def serialize(obj: SidechainBlockHeader, w: Writer): Unit = {
    w.put(obj.version)

    w.putBytes(idToBytes(obj.parentId))

    w.putLong(obj.timestamp)

    val forgingStakeInfoBytes = ForgingStakeInfoSerializer.toBytes(obj.forgingStakeInfo)
    w.putInt(forgingStakeInfoBytes.length)
    w.putBytes(forgingStakeInfoBytes)

    val forgingStakeMerklePathBytes = obj.forgingStakeMerklePath.bytes()
    w.putInt(forgingStakeMerklePathBytes.length)
    w.putBytes(forgingStakeMerklePathBytes)

    val vrfProofBytes = obj.vrfProof.bytes // TODO: replace with VRFProofSerializer... later
    w.putBytes(vrfProofBytes)

    w.putBytes(obj.sidechainTransactionsMerkleRootHash)

    w.putBytes(obj.mainchainMerkleRootHash)

    w.putBytes(obj.ommersMerkleRootHash)

    w.putLong(obj.ommersCumulativeScore)

    val signatureBytes = Signature25519Serializer.getSerializer.toBytes(obj.signature)
    w.putInt(signatureBytes.length)
    w.putBytes(signatureBytes)
  }

  override def parse(r: Reader): SidechainBlockHeader = {
    val version: Block.Version = r.getByte()

    val parentId: ModifierId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    val timestamp: Block.Timestamp = r.getLong()

    val forgingStakeInfoBytesLength: Int = r.getInt()
    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfoSerializer.parseBytes(r.getBytes(forgingStakeInfoBytesLength))

    val forgingStakeMerklePathBytesLength: Int = r.getInt()
    val forgingStakeMerkle: MerklePath = MerklePath.parseBytes(r.getBytes(forgingStakeMerklePathBytesLength))

    val vrfProofBytesLength: Int = CryptoLibProvider.vrfFunctions.vrfProofLen()
    val vrfProof: VrfProof = VrfProof.parse(r.getBytes(vrfProofBytesLength))

    val sidechainTransactionsMerkleRootHash = r.getBytes(NodeViewModifier.ModifierIdSize)

    val mainchainMerkleRootHash = r.getBytes(NodeViewModifier.ModifierIdSize)

    val ommersMerkleRootHash = r.getBytes(NodeViewModifier.ModifierIdSize)

    val ommersCumulativeScore: Long = r.getLong()

    val signatureLength: Int = r.getInt()
    val signature: Signature25519 = Signature25519Serializer.getSerializer.parseBytes(r.getBytes(signatureLength))

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
      signature)
  }
}
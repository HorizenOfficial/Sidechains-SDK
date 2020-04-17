package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.box.{ForgerBox, ForgerBoxSerializer}
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, Signature25519Serializer}
import com.horizen.serialization.{ScorexModifierIdSerializer, Views}
import com.horizen.utils.{MerklePath, MerklePathSerializer}
import com.horizen.validation.InvalidSidechainBlockHeaderException
import com.horizen.vrf.{VRFProof, VRFProofSerializer}
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
                                 forgerBox: ForgerBox,
                                 @JsonSerialize(using = classOf[MerklePathSerializer]) forgerBoxMerklePath: MerklePath,
                                 @JsonSerialize(using = classOf[VRFProofSerializer]) vrfProof: VRFProof,
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
      forgerBox.id(),
      vrfProof.bytes, // TO DO: is it ok or define vrfProof.id() ?
      forgerBoxMerklePath.bytes(), // TO DO: is it ok?
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
    if(!signature.isValid(forgerBox.rewardProposition(), messageToSign))
      throw new InvalidSidechainBlockHeaderException(s"SidechainBlockHeader $id signature is invalid.")
  }
}


object SidechainBlockHeaderSerializer extends ScorexSerializer[SidechainBlockHeader] {
  override def serialize(obj: SidechainBlockHeader, w: Writer): Unit = {
    w.put(obj.version)

    w.putBytes(idToBytes(obj.parentId))

    w.putLong(obj.timestamp)

    val forgerBoxBytes = ForgerBoxSerializer.getSerializer.toBytes(obj.forgerBox)
    w.putInt(forgerBoxBytes.length)
    w.putBytes(forgerBoxBytes)

    val forgerBoxMerklePathBytes = obj.forgerBoxMerklePath.bytes()
    w.putInt(forgerBoxMerklePathBytes.length)
    w.putBytes(forgerBoxMerklePathBytes)

    val vrfProofBytes = obj.vrfProof.bytes // TODO: replace with VRFProofSerializer... later
    w.putInt(vrfProofBytes.length)
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

    val forgerBoxBytesLength: Int = r.getInt()
    val forgerBox: ForgerBox = ForgerBoxSerializer.getSerializer.parseBytes(r.getBytes(forgerBoxBytesLength))

    val forgerBoxMerklePathBytesLength: Int = r.getInt()
    val forgerBoxMerklePath: MerklePath = MerklePath.parseBytes(r.getBytes(forgerBoxMerklePathBytesLength))

    val vrfProofBytesLength: Int = r.getInt()
    val vrfProof: VRFProof = VRFProof.parseBytes(r.getBytes(vrfProofBytesLength))

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
      forgerBox,
      forgerBoxMerklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommersCumulativeScore,
      signature)
  }
}
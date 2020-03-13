package com.horizen.block

import java.time.Instant

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.box.{ForgerBox, ForgerBoxSerializer}
import com.horizen.proof.{Signature25519, Signature25519Serializer}
import com.horizen.serialization.{ScorexModifierIdSerializer, Views}
import com.horizen.utils.{MerklePath, MerklePathSerializer}
import com.horizen.vrf.{VRFProof, VRFProofSerializer}
import scorex.core.block.Block
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.core.{NodeViewModifier, bytesToId, idToBytes}
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}

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
                                 mainchainMerkleRootHash: Array[Byte], // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders (ref headers and next headers) root hash
                                 ommersMerkleRootHash: Array[Byte], // build on top of Ommer.id()
                                 ommersNumber: Int, // to be able to calculate the score of the block without having the full SB. For future
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
      Ints.toByteArray(ommersNumber)
    )
  }

  // TODO verify if it's enough
  def semanticValidity(): Boolean = {
    if(parentId == null || parentId.length != 64
      || sidechainTransactionsMerkleRootHash == null || sidechainTransactionsMerkleRootHash.length != 32
      || mainchainMerkleRootHash == null || mainchainMerkleRootHash.length != 32
      || ommersMerkleRootHash == null || ommersMerkleRootHash.length != 32 || ommersNumber < 0
      || forgerBox == null || signature == null)
      return false

    // TODO: do we need to allow Blocks from nearest future as in MC for Ouroboros?
    // Check if timestamp is valid and not too far in the future
    if(timestamp <= 0 || timestamp > Instant.now.getEpochSecond + 2 * 60 * 60) // 2 * 60 * 60 like in Horizen MC
      return false

    // check, that signature is valid
    if(!signature.isValid(forgerBox.rewardProposition(), messageToSign))
      return false

    true
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

    w.putInt(obj.ommersNumber)

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

    val ommersNumber: Int = r.getInt()

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
      ommersNumber,
      signature)
  }
}
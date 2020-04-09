package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.serialization.{JsonMerklePathSerializer, Views}
import com.horizen.utils.MerklePath
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

@JsonView(Array(classOf[Views.Default]))
case class NeighbourProof
(sidechainId: Array[Byte],
 txsHash: Array[Byte],
 wcertHash: Array[Byte],
 leafIndex: Int,
 @JsonProperty("merklePath")
 @JsonSerialize(using = classOf[JsonMerklePathSerializer])
 merklePath: MerklePath
)
  extends BytesSerializable
{
  override type M = NeighbourProof

  override def serializer: ScorexSerializer[NeighbourProof] = NeighbourProofSerializer
}

object NeighbourProofSerializer extends ScorexSerializer[NeighbourProof] {

  override def serialize(proof: NeighbourProof, w: Writer): Unit = {
    w.putInt(proof.sidechainId.length)
    w.putBytes(proof.sidechainId)
    w.putInt(proof.txsHash.length)
    w.putBytes(proof.txsHash)
    w.putInt(proof.wcertHash.length)
    w.putBytes(proof.wcertHash)
    w.putInt(proof.leafIndex)
    w.putInt(proof.merklePath.bytes().length)
    w.putBytes(proof.merklePath.bytes())
  }

  override def parse(r: Reader): NeighbourProof = {

    val sidechainIdSize = r.getInt()
    val sidechainId = r.getBytes(sidechainIdSize)

    val txsHashSize = r.getInt()
    val txsHash = r.getBytes(txsHashSize)

    val wcertHashSize = r.getInt()
    val wcertHash = r.getBytes(wcertHashSize)

    val leafIndex = r.getInt()

    val merklePathSize = r.getInt()
    val merklePath = MerklePath.parseBytes(r.getBytes(merklePathSize))

    NeighbourProof(sidechainId, txsHash, wcertHash, leafIndex, merklePath)
  }
}

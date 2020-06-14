package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.serialization.{JsonMerklePathSerializer, Views}
import com.horizen.utils.MerklePath
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

@JsonView(Array(classOf[Views.Default]))
case class SidechainCommitmentEntryProof(sidechainId: Array[Byte],
                                         txsHash: Array[Byte],
                                         wcertHash: Array[Byte],
                                         @JsonProperty("merklePath")
                                         @JsonSerialize(using = classOf[JsonMerklePathSerializer])
                                         merklePath: MerklePath
                                        )
  extends BytesSerializable
{
  override type M = SidechainCommitmentEntryProof

  override def serializer: ScorexSerializer[SidechainCommitmentEntryProof] = SidechainCommitmentEntryProofSerializer
}

object SidechainCommitmentEntryProofSerializer extends ScorexSerializer[SidechainCommitmentEntryProof]
{
  override def serialize(proof: SidechainCommitmentEntryProof, w: Writer): Unit = {
    w.putBytes(proof.sidechainId)
    w.putBytes(proof.txsHash)
    w.putBytes(proof.wcertHash)
    w.putInt(proof.merklePath.bytes().length)
    w.putBytes(proof.merklePath.bytes())
  }

  override def parse(r: Reader): SidechainCommitmentEntryProof = {
    val sidechainId = r.getBytes(32)
    val txsHash = r.getBytes(32)
    val wcertHash = r.getBytes(32)
    val merklePathSize = r.getInt()
    val merklePath = MerklePath.parseBytes(r.getBytes(merklePathSize))

    SidechainCommitmentEntryProof(sidechainId, txsHash, wcertHash, merklePath)
  }
}

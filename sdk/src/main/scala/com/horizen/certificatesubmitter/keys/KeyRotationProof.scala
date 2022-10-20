package com.horizen.certificatesubmitter.keys

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.certificatesubmitter.keys.KeyRotationProofType.KeyRotationProofType
import com.horizen.proof.{SchnorrProof, SchnorrSignatureSerializer}
import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import com.horizen.serialization.Views
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

@JsonView(Array(classOf[Views.Default]))
case class KeyRotationProof(keyType: KeyRotationProofType, index: Int = 0, newValueOfKey: SchnorrProposition,
                            signingKeySignature: SchnorrProof, masterKeySignature: SchnorrProof) extends BytesSerializable {

  override type M = KeyRotationProof

  override def serializer: SparkzSerializer[KeyRotationProof] = KeyRotationProofSerializer
}

object KeyRotationProofType extends Enumeration {
  type KeyRotationProofType = Value

  val SigningKeyRotationProofType, MasterKeyRotationProofType = Value
}


object KeyRotationProofSerializer extends SparkzSerializer[KeyRotationProof] {
  override def serialize(keyRotationProof: KeyRotationProof, writer: Writer): Unit = {
    writer.putInt(keyRotationProof.keyType.id)
    writer.putInt(keyRotationProof.index)
    SchnorrPropositionSerializer.getSerializer.serialize(keyRotationProof.newValueOfKey, writer)
    SchnorrSignatureSerializer.getSerializer.serialize(keyRotationProof.signingKeySignature, writer)
    SchnorrSignatureSerializer.getSerializer.serialize(keyRotationProof.masterKeySignature, writer)
  }

  override def parse(reader: Reader): KeyRotationProof = {
    val keyType = KeyRotationProofType.apply(reader.getInt())
    val index = reader.getInt()
    val newValueOfKey = SchnorrPropositionSerializer.getSerializer.parse(reader)
    val signingKeySignature = SchnorrSignatureSerializer.getSerializer.parse(reader)
    val masterKeySignature = SchnorrSignatureSerializer.getSerializer.parse(reader)
    KeyRotationProof(keyType, index, newValueOfKey, signingKeySignature, masterKeySignature)
  }
}

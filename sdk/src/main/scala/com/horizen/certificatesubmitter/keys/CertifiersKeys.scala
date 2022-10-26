package com.horizen.certificatesubmitter.keys

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import com.horizen.serialization.Views
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

@JsonView(Array(classOf[Views.Default]))
case class CertifiersKeys(signingKeys: Vector[SchnorrProposition], masterKeys: Vector[SchnorrProposition]) extends BytesSerializable {
  override type M = CertifiersKeys

  override def serializer: SparkzSerializer[CertifiersKeys] = CertifiersKeysSerializer
}

object CertifiersKeysSerializer extends SparkzSerializer[CertifiersKeys] {
  override def serialize(actualKeys: CertifiersKeys, writer: Writer): Unit = {
    writer.putInt(actualKeys.signingKeys.length)
    actualKeys.signingKeys.foreach(SchnorrPropositionSerializer.getSerializer.serialize(_, writer))
    writer.putInt(actualKeys.masterKeys.length)
    actualKeys.masterKeys.foreach(SchnorrPropositionSerializer.getSerializer.serialize(_, writer))
  }

  override def parse(reader: Reader): CertifiersKeys = {
    val signingKeysArraySize: Int = reader.getInt()
    val signingKeys: Seq[SchnorrProposition] = for(_ <- 0 until signingKeysArraySize) yield {
      SchnorrPropositionSerializer.getSerializer.parse(reader)
    }

    val masterKeysArraySize: Int = reader.getInt()
    val masterKeys: Seq[SchnorrProposition] = for (_ <- 0 until masterKeysArraySize) yield {
      SchnorrPropositionSerializer.getSerializer.parse(reader)
    }

    CertifiersKeys(signingKeys.toVector, masterKeys.toVector)
  }
}

package com.horizen.certificatesubmitter.keys

import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

case class ActualKeys(signingKeys: Vector[SchnorrProposition], masterKeys: Vector[SchnorrProposition]) extends BytesSerializable {
  override type M = ActualKeys

  override def serializer: SparkzSerializer[ActualKeys] = ActualKeysSerializer
}

object ActualKeysSerializer extends SparkzSerializer[ActualKeys] {
  override def serialize(actualKeys: ActualKeys, writer: Writer): Unit = {
    writer.putInt(actualKeys.signingKeys.length)
    actualKeys.signingKeys.foreach(SchnorrPropositionSerializer.getSerializer.serialize(_, writer))
    writer.putInt(actualKeys.masterKeys.length)
    actualKeys.masterKeys.foreach(SchnorrPropositionSerializer.getSerializer.serialize(_, writer))
  }

  override def parse(reader: Reader): ActualKeys = {
    val signingKeysArraySize: Int = reader.getInt()
    val signingKeys: Seq[SchnorrProposition] = for(_ <- 0 until signingKeysArraySize) yield {
      SchnorrPropositionSerializer.getSerializer.parse(reader)
    }

    val masterKeysArraySize: Int = reader.getInt()
    val masterKeys: Seq[SchnorrProposition] = for (_ <- 0 until masterKeysArraySize) yield {
      SchnorrPropositionSerializer.getSerializer.parse(reader)
    }

    ActualKeys(signingKeys.toVector, masterKeys.toVector)
  }
}

package io.horizen.certificatesubmitter.keys

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import io.horizen.json.Views
import io.horizen.utils.ListSerializer
import sparkz.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import scala.collection.JavaConverters._

import scala.collection.JavaConverters.asScalaBufferConverter
@JsonView(Array(classOf[Views.Default]))
case class CertifiersKeys(signingKeys: Vector[SchnorrProposition], masterKeys: Vector[SchnorrProposition]) extends BytesSerializable {
  override type M = CertifiersKeys

  override def serializer: SparkzSerializer[CertifiersKeys] = CertifiersKeysSerializer
}

object CertifiersKeysSerializer extends SparkzSerializer[CertifiersKeys] {
  private val listSerializer = new ListSerializer[SchnorrProposition](SchnorrPropositionSerializer.getSerializer)
  override def serialize(actualKeys: CertifiersKeys, writer: Writer): Unit = {
    listSerializer.serialize(actualKeys.signingKeys.toList.asJava, writer)
    listSerializer.serialize(actualKeys.masterKeys.toList.asJava, writer)
  }

  override def parse(reader: Reader): CertifiersKeys = {
    val signingKeys: Seq[SchnorrProposition] = listSerializer.parse(reader).asScala
    val masterKeys: Seq[SchnorrProposition] = listSerializer.parse(reader).asScala
    CertifiersKeys(signingKeys.toVector, masterKeys.toVector)
  }
}

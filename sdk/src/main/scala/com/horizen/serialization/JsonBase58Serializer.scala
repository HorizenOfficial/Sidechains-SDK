package com.horizen.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import com.horizen.params.TestNetParams
import com.horizen.utils.BytesUtils

class JsonBase58Serializer extends JsonSerializer[Array[Byte]] {
  override def serialize(bytes: Array[Byte], jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    // TODO: why we should reverse Horized address pub key hash???????
    jsonGenerator.writeString(BytesUtils.toHorizenPublicKeyAddress(BytesUtils.reverseBytes(bytes), JsonBase58Serializer.testNetParams))
  }
}

object JsonBase58Serializer {
  // TODO: fix this: we should serialize Horizen public key hash depened on Network type
  // Unfortunately we have no easy way to access Network params from here.
  private val testNetParams = TestNetParams()
}

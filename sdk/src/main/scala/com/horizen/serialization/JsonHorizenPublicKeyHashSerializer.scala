package com.horizen.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import com.horizen.params.TestNetParams
import com.horizen.utils.BytesUtils

// Serialize public key hash bytes as Horizen standard public key address base58 string
class JsonHorizenPublicKeyHashSerializer extends JsonSerializer[Array[Byte]] {
  override def serialize(publicKeyHash: Array[Byte], jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    jsonGenerator.writeString(BytesUtils.toHorizenPublicKeyAddress(publicKeyHash, JsonHorizenPublicKeyHashSerializer.testNetParams))
  }
}

object JsonHorizenPublicKeyHashSerializer {
  // TODO: fix this: we should serialize Horizen public key hash depend on Network type
  // In MainNet we will display Horizen public key address wrongly!
  // Unfortunately we have no easy way to access Network params from here.
  private val testNetParams = TestNetParams()
}

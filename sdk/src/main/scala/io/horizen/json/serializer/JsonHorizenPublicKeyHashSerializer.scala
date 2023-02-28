package io.horizen.json.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.utils.BytesUtils

// Serialize public key hash bytes as Horizen standard public key address base58 string
// Note: Horizen address depends on the network type
class JsonHorizenPublicKeyHashSerializer extends JsonSerializer[Array[Byte]] {
  override def serialize(publicKeyHash: Array[Byte], jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    jsonGenerator.writeString(BytesUtils.toHorizenPublicKeyAddress(publicKeyHash, JsonHorizenPublicKeyHashSerializer.params))
  }
}

object JsonHorizenPublicKeyHashSerializer {
  private var params: NetworkParams = RegTestParams() // by default

  def setNetworkType(networkParams: NetworkParams): Unit = {
    params = networkParams
  }
}

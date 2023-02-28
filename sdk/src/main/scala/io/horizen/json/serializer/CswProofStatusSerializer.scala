package io.horizen.json.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import com.horizen.utxo.csw.CswManager.Responses.ProofStatus

class CswProofStatusSerializer extends JsonSerializer[ProofStatus] {
  override def serialize(status: ProofStatus, jsonGenerator: JsonGenerator, serializers: SerializerProvider): Unit = {
    jsonGenerator.writeString(status.toString())
  }
}

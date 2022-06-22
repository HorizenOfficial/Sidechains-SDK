package com.horizen.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import scorex.core.consensus.ModifierSemanticValidity

class ModifierSemanticValiditySerializer extends JsonSerializer[ModifierSemanticValidity] {
  override def serialize(semanticValidity: ModifierSemanticValidity, jsonGenerator: JsonGenerator, serializers: SerializerProvider): Unit = {
    jsonGenerator.writeString(semanticValidity.toString)

  }
}

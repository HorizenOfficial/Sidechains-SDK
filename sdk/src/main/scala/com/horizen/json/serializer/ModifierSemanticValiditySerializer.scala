package com.horizen.json.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import sparkz.core.consensus.ModifierSemanticValidity

class ModifierSemanticValiditySerializer extends JsonSerializer[ModifierSemanticValidity] {
  override def serialize(semanticValidity: ModifierSemanticValidity, jsonGenerator: JsonGenerator, serializers: SerializerProvider): Unit = {
    jsonGenerator.writeString(semanticValidity.toString)

  }
}

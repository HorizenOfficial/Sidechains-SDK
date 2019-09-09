package com.horizen.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import scorex.core.utils.ScorexEncoder
import scorex.util.ModifierId

class ScorexModifierIdEncoderSerializer extends JsonSerializer[ModifierId] {
  override def serialize(t: ModifierId, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    var encoder = new ScorexEncoder
    jsonGenerator.writeString(encoder.encode(t))
  }
}

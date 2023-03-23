package io.horizen.json.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import io.horizen.utils.BytesUtils
import sparkz.util.{ModifierId, idToBytes}

class SparkzModifierIdSerializer extends JsonSerializer[ModifierId] {
  override def serialize(t: ModifierId, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    jsonGenerator.writeString(BytesUtils.toHexString(idToBytes(t)))
  }
}

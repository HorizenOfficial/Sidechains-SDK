package com.horizen.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind
import com.fasterxml.jackson.databind.SerializerProvider
import com.horizen.utils.{BytesUtils, MerklePath}

import scala.collection.JavaConverters._

class JsonMerklePathSerializer extends databind.JsonSerializer[MerklePath] {

  override def serialize(t: MerklePath, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    var listOfPair : Iterable[Pair] = t.merklePathList.asScala.map(k => Pair(k.getKey.toString, BytesUtils.toHexString(k.getValue)))
    jsonGenerator.writeObject(listOfPair)
  }
}
class JsonMerklePathOptionSerializer extends databind.JsonSerializer[Option[MerklePath]] {

  override def serialize(t: Option[MerklePath], jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    if(t.isDefined){
      new JsonMerklePathSerializer().serialize(t.get, jsonGenerator, serializerProvider)
    }else{
      jsonGenerator.writeStartArray()
      jsonGenerator.writeEndArray()
    }
  }
}

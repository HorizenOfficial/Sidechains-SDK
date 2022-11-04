package com.horizen.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import com.horizen.account.api.rpc.request.RpcId

class RpcIdSerializer extends JsonSerializer[RpcId] {
  override def serialize(rpcId: RpcId, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    if(rpcId.getLongId!=null) {
      jsonGenerator.writeNumber(rpcId.getLongId)
    }
    if(rpcId.getStringId!=null)
      jsonGenerator.writeString(rpcId.getStringId)
  }

}
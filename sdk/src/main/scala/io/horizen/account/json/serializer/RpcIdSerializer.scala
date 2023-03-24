package io.horizen.account.json.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import io.horizen.account.api.rpc.request.RpcId

class RpcIdSerializer extends JsonSerializer[RpcId] {
  override def serialize(rpcId: RpcId, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    if(rpcId.getLongId!=null)
      jsonGenerator.writeNumber(rpcId.getLongId)
    else if(rpcId.getStringId!=null)
      jsonGenerator.writeString(rpcId.getStringId)
    else
      jsonGenerator.writeNull()
  }

}

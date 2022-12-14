package com.horizen.account.api.rpc.response

import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.account.api.rpc.request.RpcId
import com.horizen.account.api.rpc.types.Quantity
import com.horizen.account.api.rpc.utils.RpcError
import com.horizen.serialization.{ApplicationJsonSerializer, SerializationUtil}
import org.junit.Assert.assertEquals
import org.junit.Test

class RpcResponseSerializationTest {

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  @Test
  def rpcResponseSuccessNumericId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("32"))
    val result = new Quantity(1997L)
    val response = new RpcResponseSuccess(rpcId, result)
    val serializedResponse = SerializationUtil.serialize(response)
    val rpcResponse = mapper.readTree(serializedResponse)
    assertEquals(stringFromJsonNode(rpcResponse.get("jsonrpc").toString), "2.0")
    assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "32")
    assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "0x7cd")
  }

  @Test
  def rpcResponseSuccessStringId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("\"xyz\""))
    val result = new Quantity(1997L)
    val response = new RpcResponseSuccess(rpcId, result)
    val serializedResponse = SerializationUtil.serialize(response)
    val rpcResponse = mapper.readTree(serializedResponse)
    assertEquals(stringFromJsonNode(rpcResponse.get("jsonrpc").toString), "2.0")
    assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "xyz")
    assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "0x7cd")
  }

  @Test
  def rpcResponseSuccessNullResult(): Unit = {
    val rpcId = new RpcId(mapper.readTree("40"))
    val response = new RpcResponseSuccess(rpcId, None)
    val serializedResponse = SerializationUtil.serialize(response)
    val rpcResponse = mapper.readTree(serializedResponse)
    assertEquals(stringFromJsonNode(rpcResponse.get("jsonrpc").toString), "2.0")
    assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "40")
    assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "null")
  }

  @Test
  def rpcResponseErrorNumericId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("48"))
    val error = new RpcError(-32601, "Method not found", null)
    val response = new RpcResponseError(rpcId, error)
    val serializedResponse = SerializationUtil.serialize(response)
    val rpcResponse = mapper.readTree(serializedResponse)
    assertEquals(stringFromJsonNode(rpcResponse.get("jsonrpc").toString), "2.0")
    assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "48")
    assertEquals(stringFromJsonNode(rpcResponse.get("error").get("code").toString), "-32601")
    assertEquals(stringFromJsonNode(rpcResponse.get("error").get("message").toString), "Method not found")
  }

  @Test
  def rpcResponseErrorStringId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("\"xyz\""))
    val error = new RpcError(-32601, "Method not found", null)
    val response = new RpcResponseError(rpcId, error)
    val serializedResponse = SerializationUtil.serialize(response)
    val rpcResponse = mapper.readTree(serializedResponse)
    assertEquals(stringFromJsonNode(rpcResponse.get("jsonrpc").toString), "2.0")
    assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "xyz")
    assertEquals(stringFromJsonNode(rpcResponse.get("error").get("code").toString), "-32601")
    assertEquals(stringFromJsonNode(rpcResponse.get("error").get("message").toString), "Method not found")
  }

  private def stringFromJsonNode(jsonString: String): String = {
    jsonString.replace("\"", "")
  }

}

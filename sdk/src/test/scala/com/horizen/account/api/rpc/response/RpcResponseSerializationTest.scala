package com.horizen.account.api.rpc.response

import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.account.AccountFixture
import com.horizen.account.api.rpc.request.RpcId
import com.horizen.account.api.rpc.utils.RpcError
import com.horizen.serialization.ApplicationJsonSerializer
import org.junit.Test

import java.math.BigInteger

class RpcResponseSerializationTest extends AccountFixture {

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  @Test
  def rpcResponseSuccessNumericId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("32"))
    val result = BigInteger.valueOf(1997)
    val response = new RpcResponseSuccess(rpcId, result)
    assertJsonEquals("""{"jsonrpc":"2.0","id":32,"result":"0x7cd"}""", response)
  }

  @Test
  def rpcResponseSuccessStringId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("\"xyz\""))
    val result = BigInteger.valueOf(1997)
    val response = new RpcResponseSuccess(rpcId, result)
    assertJsonEquals("""{"jsonrpc":"2.0","id":"xyz","result":"0x7cd"}""", response)
  }

  @Test
  def rpcResponseSuccessNullResult(): Unit = {
    val rpcId = new RpcId(mapper.readTree("40"))
    val response = new RpcResponseSuccess(rpcId, null)
    assertJsonEquals("""{"jsonrpc":"2.0","id":40,"result":null}""", response)
  }

  @Test
  def rpcResponseErrorNumericId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("48"))
    val error = new RpcError(-32601, "Method not found", null)
    val response = new RpcResponseError(rpcId, error)
    assertJsonEquals("""{"jsonrpc":"2.0","id":48,"error":{"code":-32601,"message":"Method not found"}}""", response)
  }

  @Test
  def rpcResponseErrorStringId(): Unit = {
    val rpcId = new RpcId(mapper.readTree("\"xyz\""))
    val error = new RpcError(-32601, "Method not found", null)
    val response = new RpcResponseError(rpcId, error)
    assertJsonEquals("""{"jsonrpc":"2.0","id":"xyz","error":{"code":-32601,"message":"Method not found"}}""", response)
  }
}

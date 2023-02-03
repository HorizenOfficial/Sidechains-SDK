package com.horizen.account.api.http

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.MalformedRequestContentRejection
import com.horizen.serialization.SerializationUtil
import org.junit.Assert.{assertEquals, _}
import org.web3j.utils.Numeric

import java.math.BigInteger

class AccountEthRpcRouteTest extends AccountEthRpcRouteMock {
  
  "The Api should to" should {

    val checkChainId = Numeric.toHexStringWithPrefix(BigInteger.valueOf(params.chainId))

    "reply at /ethv1 - bad api token header" in {
      Post(basePath)
        .addCredentials(badCredentials)
        .withEntity(SerializationUtil.serialize("maybe_a_json")) ~> ethRpcRoute ~> check {
          rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
        }
      }

    val singleRequestJson = "{\"id\":\"196\",\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[]}}"
    val singleRequestJsonNode = mapper.readTree(singleRequestJson)
    "reply at /ethv1 - single request" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(singleRequestJsonNode)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("result").toString),checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString),"196")
      }
    }

    val singleRequestJsonMethodNotFound = "{\"id\":\"225\",\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId_\",\"params\":[]}}"
    val singleRequestJsonNodeMethodNotFound = mapper.readTree(singleRequestJsonMethodNotFound)
    "reply at /ethv1 - single request - method not found" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(singleRequestJsonNodeMethodNotFound)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("code").toString), "-32601")
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("message").toString), "Method not found")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "225")
      }
    }

    val singleRequestJsonIdNotPresent = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId_\",\"params\":[]}}"
    val singleRequestJsonNodeIdNotPresent = mapper.readTree(singleRequestJsonIdNotPresent)
    "reply at /ethv1 - single request - id not present" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(singleRequestJsonNodeIdNotPresent)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "null")
      }
    }

    val batchRequestJsonOne = "[{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":8},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":16}]"
    val batchRequestJsonNodeOne = mapper.readTree(batchRequestJsonOne)
    "reply at /ethv1 - batch request" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeOne)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("result").toString),checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "8")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "16")
      }
    }

    val batchRequestJsonTwo = "[{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":8}," +
      "{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":16},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":24}," +
      "{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":32},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":40}]"
    val batchRequestJsonNodeTwo = mapper.readTree(batchRequestJsonTwo)
    "reply at /ethv1 - batch request two" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeTwo)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "8")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "16")
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("id").toString), "24")
        assertEquals(stringFromJsonNode(rpcResponse.get(3).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(3).get("id").toString), "32")
        assertEquals(stringFromJsonNode(rpcResponse.get(4).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(4).get("id").toString), "40")
      }
    }

    val batchRequestJsonMethodNotFound = "[{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId_\",\"params\":[],\"id\":8},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":16}]"
    val batchRequestJsonNodeMethodNotFound = mapper.readTree(batchRequestJsonMethodNotFound)
    "reply at /ethv1 - batch request - method not found" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeMethodNotFound)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("error").get("code").toString), "-32601")
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("error").get("message").toString), "Method not found")
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "8")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "16")
      }
    }

    val batchRequestJsonIdNotPresent = "[{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":8},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[]}]"
    val batchRequestJsonNodeIdNotPresent = mapper.readTree(batchRequestJsonIdNotPresent)
    "reply at /ethv1 - batch request - id not present" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeIdNotPresent)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "8")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "null")
      }
    }

    val batchRequestJsonInvalidOne = "[{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":8},24,{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":16}]"
    val batchRequestJsonNodeInvalidOne = mapper.readTree(batchRequestJsonInvalidOne)
    "reply at /ethv1 - batch request - invalid batch one" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeInvalidOne)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "8")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "null")
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("id").toString), "16")
      }
    }

    val batchRequestJsonInvalidTwo = "[8,16,24]"
    val batchRequestJsonNodeInvalidTwo = mapper.readTree(batchRequestJsonInvalidTwo)
    "reply at /ethv1 - batch request - invalid batch two" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeInvalidTwo)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "null")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "null")
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get(2).get("id").toString), "null")
      }
    }

    val batchRequestJsonEmptyArray= "[]"
    val batchRequestJsonNodeEmptyArray = mapper.readTree(batchRequestJsonEmptyArray)
    "reply at /ethv1 - batch request - invalid batch - empty array" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeEmptyArray)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "null")
      }
    }

    val singleRequestJsonInvalidId = "{\"id\":65465817687165465465,\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[]}}"
    val singleRequestJsonNodeInvalidId = mapper.readTree(singleRequestJsonInvalidId)
    "reply at /ethv1 - single request - invalid id" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(singleRequestJsonNodeInvalidId)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "null")
      }
    }

    val batchRequestJsonInvalidId = "[{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":-258},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":16}]"
    val batchRequestJsonNodeInvalidId = mapper.readTree(batchRequestJsonInvalidId)
    "reply at /ethv1 - batch request - invalid id" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeInvalidId)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("error").get("code").toString), "-32600")
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("error").get("message").toString), "Invalid request")
        assertEquals(stringFromJsonNode(rpcResponse.get(0).get("id").toString), "null")
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("result").toString), checkChainId)
        assertEquals(stringFromJsonNode(rpcResponse.get(1).get("id").toString), "16")
      }
    }

    // Uncle Blocks RPCs tests
    val batchRequestJsonGetUncleCountByBlockHash = "{\"id\":\"8\",\"jsonrpc\":\"2.0\",\"method\":\"eth_getUncleCountByBlockHash\",\"params\":[\"0x518637c0ac365cdc5fc7e632aebc386ccea10dddd5e58cdf127b2d48d085f5a5\"]}}"
    val batchRequestJsonNodeGetUncleCountByBlockHash = mapper.readTree(batchRequestJsonGetUncleCountByBlockHash)
    "reply at /ethv1 - uncle block - eth_getUncleCountByBlockHash" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeGetUncleCountByBlockHash)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "0x0")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "8")
      }
    }

    val batchRequestJsonGetUncleCountByBlockNumber = "{\"id\":\"16\",\"jsonrpc\":\"2.0\",\"method\":\"eth_getUncleCountByBlockNumber\",\"params\":[\"0xf8\"]}}"
    val batchRequestJsonNodeGetUncleCountByBlockNumber = mapper.readTree(batchRequestJsonGetUncleCountByBlockNumber)
    "reply at /ethv1 - uncle block - eth_getUncleCountByBlockNumber" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeGetUncleCountByBlockNumber)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "0x0")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "16")
      }
    }

    val batchRequestJsonGetUncleByBlockHashAndIndex = "{\"id\":\"24\",\"jsonrpc\":\"2.0\",\"method\":\"eth_getUncleByBlockHashAndIndex\",\"params\":[\"0x518637c0ac365cdc5fc7e632aebc386ccea10dddd5e58cdf127b2d48d085f5a5\",\"0x0\"]}}"
    val batchRequestJsonNodeGetUncleByBlockHashAndIndex = mapper.readTree(batchRequestJsonGetUncleByBlockHashAndIndex)
    "reply at /ethv1 - uncle block - eth_getUncleByBlockHashAndIndex" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeGetUncleByBlockHashAndIndex)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "null")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "24")
      }
    }

    val batchRequestJsonGetUncleByBlockNumberAndIndex = "{\"id\":\"32\",\"jsonrpc\":\"2.0\",\"method\":\"eth_getUncleByBlockNumberAndIndex\",\"params\":[\"0xf8\",\"0x0\"]}}"
    val batchRequestJsonNodeGetUncleByBlockNumberAndIndex = mapper.readTree(batchRequestJsonGetUncleByBlockNumberAndIndex)
    "reply at /ethv1 - uncle block - eth_getUncleByBlockNumberAndIndex" in {
      Post(basePath)
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(batchRequestJsonNodeGetUncleByBlockNumberAndIndex)) ~> ethRpcRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val rpcResponse = mapper.readTree(entityAs[String])
        assertEquals(stringFromJsonNode(rpcResponse.get("result").toString), "null")
        assertEquals(stringFromJsonNode(rpcResponse.get("id").toString), "32")
      }
    }

  }

  private def stringFromJsonNode(jsonString: String): String = {
    jsonString.replace("\"", "")
  }

}

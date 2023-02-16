package com.horizen.account.api.http

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.MalformedRequestContentRejection
import com.fasterxml.jackson.databind.JsonNode
import org.junit.Assert._
import org.web3j.utils.Numeric

import java.math.BigInteger

class AccountEthRpcRouteTest extends AccountEthRpcRouteMock {

  private def rpc(requestJson: String, expectedJson: String = null): JsonNode = {
    Post(basePath)
      .addCredentials(credentials)
      .withEntity(requestJson) ~> ethRpcRoute ~> check {
      status.intValue() shouldBe StatusCodes.OK.intValue
      responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      val actual = mapper.readTree(entityAs[String])
      if (expectedJson != null) {
        val expected = mapper.readTree(expectedJson)
        assertEquals("unexpected response", expected, actual)
      }
      actual
    }
  }

  "The Api" should {

    val checkChainId = Numeric.toHexStringWithPrefix(BigInteger.valueOf(params.chainId))

    "reply at /ethv1 - bad api token header" in {
      Post(basePath)
        .addCredentials(badCredentials)
        .withEntity("maybe_a_json") ~> ethRpcRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
    }

    "reply at /ethv1 - single request" in {
      rpc(
        """{"jsonrpc":"2.0","id":"196","method":"eth_chainId","params":[]}""",
        s"""{"jsonrpc":"2.0","id":"196","result":"$checkChainId"}"""
      )
    }

    "reply at /ethv1 - single request - method not found" in {
      rpc(
        """{"jsonrpc":"2.0","id":"225","method":"eth_chainId_","params":[]}""",
        """{"jsonrpc":"2.0","id":"225","error":{"code":-32601,"message":"Method not found"}}"""
      )
    }

    "reply at /ethv1 - single request - id not present" in {
      rpc(
        """{"jsonrpc":"2.0","method":"eth_chainId_","params":[]}""",
        """{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: id"}}"""
      )
    }

    "reply at /ethv1 - batch request" in {
      rpc(
        """[
          {"jsonrpc":"2.0","id":8,"method":"eth_chainId","params":[]},
          {"jsonrpc":"2.0","id":16,"method":"eth_chainId","params":[]},
          {"jsonrpc":"2.0","id":24,"method":"eth_chainId","params":[]},
          {"jsonrpc":"2.0","id":32,"method":"eth_chainId","params":[]},
          {"jsonrpc":"2.0","id":40,"method":"eth_chainId","params":[]}
        ]""",
        s"""[
          {"jsonrpc":"2.0","id":8,"result":"$checkChainId"},
          {"jsonrpc":"2.0","id":16,"result":"$checkChainId"},
          {"jsonrpc":"2.0","id":24,"result":"$checkChainId"},
          {"jsonrpc":"2.0","id":32,"result":"$checkChainId"},
          {"jsonrpc":"2.0","id":40,"result":"$checkChainId"}
        ]"""
      )
    }

    "reply at /ethv1 - batch request - method not found" in {
      rpc(
        """[
          {"jsonrpc":"2.0","id":8,"method":"eth_chainId_","params":[]},
          {"jsonrpc":"2.0","id":16,"method":"eth_chainId","params":[]}
        ]""",
        s"""[
          {"jsonrpc":"2.0","id":8,"error":{"code":-32601,"message":"Method not found"}},
          {"jsonrpc":"2.0","id":16,"result":"$checkChainId"}
        ]"""
      )
    }

    "reply at /ethv1 - batch request - id not present" in {
      rpc(
        """[
          {"jsonrpc":"2.0","id":8,"method":"eth_chainId","params":[]},
          {"jsonrpc":"2.0","method":"eth_chainId","params":[]}
        ]""",
        s"""[
          {"jsonrpc":"2.0","id":8,"result":"$checkChainId"},
          {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: id"}}
        ]"""
      )
    }

    "reply at /ethv1 - batch request - invalid batch one" in {
      rpc(
        """[
          {"jsonrpc":"2.0","id":8,"method":"eth_chainId","params":[]},
          24,
          {"jsonrpc":"2.0","id":16,"method":"eth_chainId","params":[]}
        ]""",
        s"""[
          {"jsonrpc":"2.0","id":8,"result":"$checkChainId"},
          {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: jsonrpc"}},
          {"jsonrpc":"2.0","id":16,"result":"$checkChainId"}
        ]"""
      )
    }

    "reply at /ethv1 - batch request - invalid batch two" in {
      rpc(
        """[
          8,
          16,
          24
        ]""",
        """[
          {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: jsonrpc"}},
          {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: jsonrpc"}},
          {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: jsonrpc"}}
        ]"""
      )
    }

    "reply at /ethv1 - batch request - invalid batch - empty array" in {
      rpc(
        """[]""",
        """{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"missing field: jsonrpc"}}"""
      )
    }

    "reply at /ethv1 - single request - invalid id" in {
      rpc(
        """{"jsonrpc":"2.0","id":65465817687165465465,"method":"eth_chainId","params":[]}""",
        """{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"Rpc Id value is greater than datatype max value"}}"""
      )
    }

    "reply at /ethv1 - batch request - invalid id" in {
      rpc(
        """[
          {"jsonrpc":"2.0","id":-258,"method":"eth_chainId","params":[]},
          {"jsonrpc":"2.0","id":16,"method":"eth_chainId","params":[]}
        ]""",
        s"""[
          {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request","data":"Rpc Id can't be a negative number"}},
          {"jsonrpc":"2.0","id":16,"result":"$checkChainId"}
        ]"""
      )
    }

    // Uncle Blocks RPCs tests
    "reply at /ethv1 - uncle block - eth_getUncleCountByBlockHash" in {
      rpc(
        """{"jsonrpc":"2.0","id":"8","method":"eth_getUncleCountByBlockHash","params":["0x518637c0ac365cdc5fc7e632aebc386ccea10dddd5e58cdf127b2d48d085f5a5"]}""",
        """{"jsonrpc":"2.0","id":"8","result":"0x0"}"""
      )
    }

    "reply at /ethv1 - uncle block - eth_getUncleCountByBlockNumber" in {
      rpc(
        """{"jsonrpc":"2.0","id":"16","method":"eth_getUncleCountByBlockNumber","params":["0xf8"]}""",
        """{"jsonrpc":"2.0","id":"16","result":"0x0"}"""
      )
    }

    "reply at /ethv1 - uncle block - eth_getUncleByBlockHashAndIndex" in {
      rpc(
        """{"jsonrpc":"2.0","id":"24","method":"eth_getUncleByBlockHashAndIndex","params":["0x518637c0ac365cdc5fc7e632aebc386ccea10dddd5e58cdf127b2d48d085f5a5","0x0"]}""",
        """{"jsonrpc":"2.0","id":"24","result":null}"""
      )
    }

    "reply at /ethv1 - uncle block - eth_getUncleByBlockNumberAndIndex" in {
      rpc(
        """{"jsonrpc":"2.0","id":"32","method":"eth_getUncleByBlockNumberAndIndex","params":["0xf8","0x0"]}""",
        """{"jsonrpc":"2.0","id":"32","result":null}"""
      )
    }

  }
}

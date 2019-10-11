package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import com.fasterxml.jackson.databind.JsonNode
import com.horizen.api.http.schema.INVALID_HOST_PORT
import com.horizen.api.http.schema.SidechainNodeRestSchema.{ReqConnectPost, SidechainPeerNode}
import com.horizen.serialization
import com.horizen.serialization.SerializationUtil
import org.junit.Assert.{assertEquals, assertTrue}

import scala.collection.JavaConverters._

class SidechainNodeApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/node/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainNodeApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "connect") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "connect").withEntity("maybe_a_json") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "connect") ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /allPeers" in {
      // 3 peers
      Post(basePath + "allPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("peers").isArray)
            assertEquals(3, result.get("peers").elements().asScala.length)
            val elems: Array[SidechainPeerNode] = peers.map(p => SidechainPeerNode(p._1.toString, p._2.lastSeen, p._2.peerSpec.nodeName, p._2.connectionType.map(_.toString))).toArray
            val nodes = result.get("peers").elements().asScala.toArray

            val first: JsonNode = nodes(0)
            assertEquals(first.get("address").textValue(), elems(0).address)
            assertEquals(first.get("lastSeen").asLong(), elems(0).lastSeen)
            assertEquals(first.get("name").textValue(), elems(0).name)
            assertEquals(first.get("connectionType").textValue(), elems(0).connectionType.getOrElse(""))

            val second: JsonNode = nodes(1)
            assertEquals(second.get("address").textValue(), elems(1).address)
            assertEquals(second.get("lastSeen").asLong(), elems(1).lastSeen)
            assertEquals(second.get("name").textValue(), elems(1).name)
            assertEquals(second.get("connectionType").textValue(), elems(1).connectionType.getOrElse(""))

            val third: JsonNode = nodes(2)
            assertEquals(third.get("address").textValue(), elems(2).address)
            assertEquals(third.get("lastSeen").asLong(), elems(2).lastSeen)
            assertEquals(third.get("name").textValue(), elems(2).name)
            assertEquals(third.get("connectionType").textValue(), elems(2).connectionType.getOrElse(""))
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // api error
      sidechainApiMockConfiguration.setShould_peerManager_GetAllPeers_reply(false)
      Post(basePath + "allPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /connectedPeers" in {
      // 2 peers
      Post(basePath + "connectedPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("peers").isArray)
            assertEquals(2, result.get("peers").elements().asScala.length)
            val elems: Array[SidechainPeerNode] = connectedPeers.map(p => SidechainPeerNode(p.peerSpec.address.map(_.toString).getOrElse(""), p.lastSeen, p.peerSpec.nodeName, p.connectionType.map(_.toString))).toArray
            val nodes = result.get("peers").elements().asScala.toArray
            val first: JsonNode = nodes(0)
            assertEquals(first.get("address").textValue(), elems(0).address)
            assertEquals(first.get("lastSeen").asLong(), elems(0).lastSeen)
            assertEquals(first.get("name").textValue(), elems(0).name)
            assertEquals(first.get("connectionType").textValue(), elems(0).connectionType.getOrElse(""))

            val second: JsonNode = nodes(1)
            assertEquals(second.get("address").textValue(), elems(1).address)
            assertEquals(second.get("lastSeen").asLong(), elems(1).lastSeen)
            assertEquals(second.get("name").textValue(), elems(1).name)
            assertEquals(second.get("connectionType").textValue(), elems(1).connectionType.getOrElse(""))
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // api error
      sidechainApiMockConfiguration.setShould_networkController_GetConnectedPeers_reply(false)
      Post(basePath + "connectedPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /connect" in {
      // valid host
      Post(basePath + "connect")
        .withEntity(SerializationUtil.serialize(ReqConnectPost("92.92.92.92", 8080))) ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("connectedTo").isTextual)
            assertEquals(result.get("connectedTo").textValue(), "/92.92.92.92:8080")
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // not valid host
      Post(basePath + "connect")
        .withEntity(SerializationUtil.serialize(ReqConnectPost("my_host", 8080))) ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /blacklistedPeers" in {
      Post(basePath + "blacklistedPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("addresses").isArray)
            val nodes = result.get("addresses").elements().asScala.toArray
            assertEquals(nodes(0).textValue(), inetAddrBlackListed_1.getAddress.toString)
            assertEquals(nodes(1).textValue(), inetAddrBlackListed_2.getAddress.toString)
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // api error
      sidechainApiMockConfiguration.setShould_peerManager_GetBlacklistedPeers_reply(false)
      Post(basePath + "blacklistedPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }
  }
}

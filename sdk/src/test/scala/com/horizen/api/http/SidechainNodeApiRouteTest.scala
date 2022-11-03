package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import com.fasterxml.jackson.databind.JsonNode
import com.horizen.api.http.SidechainNodeErrorResponse.ErrorBadCircuit
import com.horizen.api.http.SidechainNodeRestSchema._
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue}

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.language.postfixOps

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

      Post(basePath + "connect").withEntity("maybe_a_json") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "connect").withEntity("maybe_a_json") ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /allPeers" in {
      // 3 peers
      Post(basePath + "allPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("peers").isArray)
        assertEquals(3, result.get("peers").elements().asScala.length)
        val elems: Array[SidechainPeerNode] = peers.map(
          p => SidechainPeerNode(p._1.toString, None, p._2.lastHandshake, 0, p._2.peerSpec.nodeName, p._2.peerSpec.agentName, p._2.peerSpec.protocolVersion.toString, p._2.connectionType.map(_.toString))
        ).toArray
        val nodes = result.get("peers").elements().asScala.toArray

        val first: JsonNode = nodes(0)
        assertEquals(first.get("remoteAddress").textValue(), elems(0).remoteAddress)
        assertEquals(first.get("lastHandshake").asLong(), elems(0).lastHandshake)
        assertEquals(first.get("name").textValue(), elems(0).name)
        assertEquals(first.get("agentName").textValue(), elems(0).agentName)
        assertEquals(first.get("protocolVersion").textValue(), elems(0).protocolVersion)
        assertEquals(first.get("connectionType").textValue(), elems(0).connectionType.getOrElse(""))

        val second: JsonNode = nodes(1)
        assertEquals(second.get("remoteAddress").textValue(), elems(1).remoteAddress)
        assertEquals(second.get("lastHandshake").asLong(), elems(1).lastHandshake)
        assertEquals(second.get("name").textValue(), elems(1).name)
        assertEquals(second.get("agentName").textValue(), elems(1).agentName)
        assertEquals(second.get("protocolVersion").textValue(), elems(1).protocolVersion)
        assertEquals(second.get("connectionType").textValue(), elems(1).connectionType.getOrElse(""))

        val third: JsonNode = nodes(2)
        assertEquals(third.get("remoteAddress").textValue(), elems(2).remoteAddress)
        assertEquals(third.get("lastHandshake").asLong(), elems(2).lastHandshake)
        assertEquals(third.get("name").textValue(), elems(2).name)
        assertEquals(third.get("agentName").textValue(), elems(2).agentName)
        assertEquals(third.get("protocolVersion").textValue(), elems(2).protocolVersion)
        assertEquals(third.get("connectionType").textValue(), elems(2).connectionType.getOrElse(""))
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
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("peers").isArray)
        assertEquals(2, result.get("peers").elements().asScala.length)
        val elems: Array[Option[SidechainPeerNode]] = connectedPeers.map {
          conn =>
            conn.peerInfo.map {
              p => SidechainPeerNode(
                conn.connectionId.remoteAddress.toString,
                Some(conn.connectionId.localAddress.toString),
                p.lastHandshake,
                conn.lastMessage,
                p.peerSpec.nodeName,
                p.peerSpec.agentName,
                p.peerSpec.protocolVersion.toString,
                p.connectionType.map(_.toString)
              )
            }
        }.toArray
        val nodes = result.get("peers").elements().asScala.toArray
        val first: JsonNode = nodes(0)
        assertEquals(first.get("remoteAddress").textValue(), elems(0).get.remoteAddress)
        assertEquals(first.get("localAddress").textValue(), elems(0).get.localAddress.get)
        assertEquals(first.get("lastHandshake").asLong(), elems(0).get.lastHandshake)
        assertEquals(first.get("lastMessage").asLong(), elems(0).get.lastMessage)
        assertEquals(first.get("name").textValue(), elems(0).get.name)
        assertEquals(first.get("agentName").textValue(), elems(0).get.agentName)
        assertEquals(first.get("protocolVersion").textValue(), elems(0).get.protocolVersion)
        assertEquals(first.get("connectionType").textValue(), elems(0).get.connectionType.getOrElse(""))

        val second: JsonNode = nodes(1)
        assertEquals(second.get("remoteAddress").textValue(), elems(1).get.remoteAddress)
        assertEquals(second.get("localAddress").textValue(), elems(1).get.localAddress.get)
        assertEquals(second.get("lastHandshake").asLong(), elems(1).get.lastHandshake)
        assertEquals(second.get("lastMessage").asLong(), elems(1).get.lastMessage)
        assertEquals(second.get("name").textValue(), elems(1).get.name)
        assertEquals(second.get("agentName").textValue(), elems(1).get.agentName)
        assertEquals(second.get("protocolVersion").textValue(), elems(1).get.protocolVersion)
        assertEquals(second.get("connectionType").textValue(), elems(1).get.connectionType.getOrElse(""))
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
        .withEntity(SerializationUtil.serialize(ReqConnect("92.92.92.92", 8080))) ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("connectedTo").isTextual)
        assertEquals(result.get("connectedTo").textValue(), "/92.92.92.92:8080")
      }
      // not valid host
      Post(basePath + "connect")
        .withEntity(SerializationUtil.serialize(ReqConnect("my_host", 8080))) ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /blacklistedPeers" in {
      Post(basePath + "blacklistedPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("addresses").isArray)
        val nodes = result.get("addresses").elements().asScala.toArray
        assertEquals(nodes(0).textValue(), inetAddrBlackListed_1.getAddress.toString)
        assertEquals(nodes(1).textValue(), inetAddrBlackListed_2.getAddress.toString)
      }
      // api error
      sidechainApiMockConfiguration.setShould_peerManager_GetBlacklistedPeers_reply(false)
      Post(basePath + "blacklistedPeers") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /storageVersions" in {
      Post(basePath + "storageVersions") ~> sidechainNodeApiRoute ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")

        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements.asScala.length)
        assertTrue(result.get("listOfVersions").isObject)
        assertEquals(listOfStorageVersions.size, result.get("listOfVersions").elements.asScala.length)
        val listOfVersions = result.get("listOfVersions")

        assertTrue(listOfStorageVersions.forall(x => listOfVersions.get(x._1).asText == x._2))
      }

      sidechainApiMockConfiguration.setShould_nodeViewHolder_GetStorageVersions_reply(false)
      Post(basePath + "storageVersions") ~> sidechainNodeApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /sidechainId" in {
      Post(basePath + "sidechainId") ~> sidechainNodeApiRoute ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")

        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements.asScala.length)
        assertTrue(result.get("sidechainId").isTextual)
        assertEquals(sidechainId, result.get("sidechainId").asText())
      }
    }

    "reply at /getKeyRotationProofs" in {
      //Malformed request
      Post(basePath + "getKeyRotationProof").withEntity("maybe_a_json") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      //Bad circuit
      Post(basePath + "getKeyRotationProof").withEntity(SerializationUtil.serialize(ReqKeyRotationProof(0,0,0)))  ~> sidechainNodeApiRoute ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBadCircuit("The current circuit doesn't support key rotation proofs!", JOptional.empty()).code)
      }

      //Should answer with a KeyRotationProof
      Post(basePath + "getKeyRotationProof") .withEntity(SerializationUtil.serialize(ReqKeyRotationProof(0,0,0))) ~> sidechainNodeApiRouteWithKeyRotation ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements.asScala.length)

        val keyRotationProofJson = result.get("keyRotationProof")
        assertTrue(keyRotationProofJson.has("keyType"))
        assertEquals("SigningKeyRotationProofType", keyRotationProofJson.get("keyType").get("value").asText())
        assertEquals(keyRotationProof.index, keyRotationProofJson.get("index").asInt())
        assertTrue(keyRotationProofJson.has("newValueOfKey"))
        assertEquals(BytesUtils.toHexString(keyRotationProof.newValueOfKey.pubKeyBytes()), keyRotationProofJson.get("newValueOfKey").get("publicKey").asText())
        assertTrue(keyRotationProofJson.has("signingKeySignature"))
        assertEquals(BytesUtils.toHexString(keyRotationProof.signingKeySignature.bytes()), keyRotationProofJson.get("signingKeySignature").get("signature").asText())
        assertTrue(keyRotationProofJson.has("masterKeySignature"))
        assertEquals(BytesUtils.toHexString(keyRotationProof.masterKeySignature.bytes()), keyRotationProofJson.get("masterKeySignature").get("signature").asText())
      }

  }

    "reply at /getCertificateSigners" in {

      //Should answer with a CertificateKeys
      Post(basePath + "getCertificateSigners") ~> sidechainNodeApiRouteWithKeyRotation ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements.asScala.length)

        val certifiersKeysJson = result.get("certifiersKeys")
        assertTrue(certifiersKeysJson.has("signingKeys"))
        val signingKeys = certifiersKeysJson.get("signingKeys")
        assertEquals(certifiersKeys.signingKeys.size, signingKeys.size())
        assertEquals(BytesUtils.toHexString(certifiersKeys.signingKeys(0).pubKeyBytes()), signingKeys.get(0).get("publicKey").asText())
        assertEquals(BytesUtils.toHexString(certifiersKeys.signingKeys(1).pubKeyBytes()), signingKeys.get(1).get("publicKey").asText())

        assertTrue(certifiersKeysJson.has("masterKeys"))
        val masterKeys = certifiersKeysJson.get("masterKeys")
        assertEquals(certifiersKeys.masterKeys.size, masterKeys.size())
        assertEquals(BytesUtils.toHexString(certifiersKeys.masterKeys(0).pubKeyBytes()), masterKeys.get(0).get("publicKey").asText())
        assertEquals(BytesUtils.toHexString(certifiersKeys.masterKeys(1).pubKeyBytes()), masterKeys.get(1).get("publicKey").asText())
      }

    }

  }
}
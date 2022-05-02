package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.SidechainWalletErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.SidechainWalletRestScheme._
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert._

import scala.collection.JavaConverters._
import java.util.{Optional => JOptional}

class SidechainWalletApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/wallet/"

  "The Api" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainWalletApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "allBoxes").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allBoxes").withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "balanceOfType").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "balanceOfType").withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "allPublicKeys").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allPublicKeys").withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /allBoxes" in {
      Post(basePath + "allBoxes") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("boxes").size())
        val node = result.get("boxes")
        if (node == null)
          fail("Result serialization failed")
        assertTrue(node.isArray)
        assertEquals(allBoxes.size(), node.elements().asScala.length)
        val box_json = node.elements().asScala.toList
        for (i <- 0 to box_json.size - 1)
          jsonChecker.assertsOnBoxJson(box_json(i), allBoxes.get(i))
      }
      Post(basePath + "allBoxes")
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(Some("a_boxTypeClass"), None))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "allBoxes")
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(None, Some(Seq(
            BytesUtils.toHexString(allBoxes.get(0).id()), BytesUtils.toHexString(allBoxes.get(1).id())))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("boxes").size())
        val node = result.get("boxes")
        if (result == null)
          fail("Result serialization failed")

        assertTrue(node.isArray)
        assertEquals(allBoxes.size() - 2, node.elements().asScala.length)
      }
      Post(basePath + "allBoxes")
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(Some("a_boxTypeClass"), Some(Seq("boxId_1", "boxId_2"))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /coinsBalance" in {
      Post(basePath + "coinsBalance") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.fieldNames().asScala.length)

        val node = result.get("balance")
        if (node == null)
          fail("Result serialization failed")

        node.asInt() shouldBe 5500
      }
    }

    "reply at /balanceOfType" in {
      Post(basePath + "balanceOfType")
        .withEntity(
          SerializationUtil.serialize(ReqBalance("a_class"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /createVrfSecret" in {
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      // secret is added
      Post(basePath + "createVrfSecret") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "createVrfSecret") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretNotAdded("", JOptional.empty()).code)
      }
    }

    "reply at /createPrivateKey25519" in {
      // secret is added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      Post(basePath + "createPrivateKey25519") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "createPrivateKey25519") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretNotAdded("", JOptional.empty()).code)
      }
    }

    "reply at /allPublicKeys" in {
      Post(basePath + "allPublicKeys") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("propositions").size())
        val node = result.get("propositions")
        if (node == null) {
          fail("Result serialization failed")
        }
        assertTrue(node.isArray)
        assertEquals(2, node.findValues("publicKey").size())
      }
      Post(basePath + "allPublicKeys")
        .withEntity(
          SerializationUtil.serialize(ReqAllPropositions(Some("proptype")))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }
  }
}
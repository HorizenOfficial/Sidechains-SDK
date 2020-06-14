package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.SidechainWalletErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.SidechainWalletRestScheme._
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert._

import scala.collection.JavaConverters._

class SidechainWalletApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/wallet/"

  "The Api should to" should {

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

      Post(basePath + "balance").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "balance").withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
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
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("boxes").size())
            result.get("boxes") match {
              case node =>
                assertTrue(node.isArray)
                assertEquals(allBoxes.size(), node.elements().asScala.length)
                val box_json = node.elements().asScala.toList
                for (i <- 0 to box_json.size - 1)
                  jsonChecker.assertsOnBoxJson(box_json(i), allBoxes.get(i))
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
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
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("boxes").size())
            result.get("boxes") match {
              case node =>
                assertTrue(node.isArray)
                assertEquals(4, node.elements().asScala.length)
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      Post(basePath + "allBoxes")
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(Some("a_boxTypeClass"), Some(Seq("boxId_1", "boxId_2"))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /balance" in {
      Post(basePath + "balance") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.fieldNames().asScala.length)
            result.get("balance") match {
              case node => node.asInt() shouldBe 5500
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      Post(basePath + "balance")
        .withEntity(
          SerializationUtil.serialize(ReqBalance(Some("a_class")))) ~> sidechainWalletApiRoute ~> check {
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
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("proposition").size())
            assertEquals(1, result.path("proposition").findValues("publicKey").size())
            result.get("proposition").get("publicKey") match {
              case node => assertTrue(node.isTextual)
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "createVrfSecret") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretNotAdded("", None).code)
      }
    }

    "reply at /createPrivateKey25519" in {
      // secret is added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      Post(basePath + "createPrivateKey25519") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        fr.get("result") match {
          case result =>
            assertEquals(1, result.findValues("proposition").size())
            assertEquals(1, result.path("proposition").findValues("publicKey").size())
            result.get("proposition").get("publicKey") match {
              case node => assertTrue(node.isTextual)
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "createPrivateKey25519") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretNotAdded("", None).code)
      }
    }

    "reply at /allPublicKeys" in {
      Post(basePath + "allPublicKeys") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("propositions").size())
            result.get("propositions") match {
              case node =>
                assertTrue(node.isArray)
                assertEquals(2, node.findValues("publicKey").size())
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
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
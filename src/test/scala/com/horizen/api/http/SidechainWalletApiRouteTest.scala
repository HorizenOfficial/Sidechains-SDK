package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.schema.WalletRestScheme.{ReqAllBoxesPost, ReqAllPropositionsPost, ReqBalancePost}
import com.horizen.serialization.SerializationUtil
import com.horizen.api.http.schema.SECRET_NOT_ADDED
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

      Post(basePath + "allBoxes") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allBoxes").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allBoxes") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "balance") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "balance").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "balance") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "allPublicKeys") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allPublicKeys").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allPublicKeys") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /allBoxes" in {
        Post(basePath + "allBoxes")
          .withEntity(
            SerializationUtil.serialize(ReqAllBoxesPost(None, None))) ~> sidechainWalletApiRoute ~> check {
          status.intValue() shouldBe StatusCodes.OK.intValue
          responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
          mapper.readTree(entityAs[String]).get("result") match {
            case result =>
              assertEquals(1, result.findValues("boxes").size())
              result.get("boxes") match{
                case node =>
                  assertTrue(node.isArray)
                  assertEquals(3, node.elements().asScala.length)
                case _ => fail("Result serialization failed")
              }
            case _ => fail("Serialization failed for object SidechainApiResponseBody")
          }
        }
        Post(basePath + "allBoxes")
          .withEntity(
            SerializationUtil.serialize(ReqAllBoxesPost(Some("a_boxTypeClass"), None))) ~> sidechainWalletApiRoute ~> check {
          status.intValue() shouldBe StatusCodes.InternalServerError.intValue
          responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        }
      Post(basePath + "allBoxes")
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxesPost(None, Some(Seq("box_1_id", "box_2_id"))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("boxes").size())
            result.get("boxes") match{
              case node =>
                assertTrue(node.isArray)
                assertEquals(1, node.elements().asScala.length)
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      Post(basePath + "allBoxes")
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxesPost(Some("a_boxTypeClass"), Some(Seq("boxId_1", "boxId_2"))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /balance" in {
      Post(basePath + "balance")
        .withEntity(
          SerializationUtil.serialize(ReqBalancePost(None))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.fieldNames().asScala.length)
            result.get("balance") match{
              case node => node.asInt() shouldBe 5500
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      Post(basePath + "balance")
        .withEntity(
          SerializationUtil.serialize(ReqBalancePost(Some("a_class")))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /newPublicKey" in {
      // secret is added
      Post(basePath + "newPublicKey") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("proposition").size())
            assertEquals(1, result.path("proposition").findValues("publicKey").size())
            result.get("proposition").get("publicKey") match{
              case node => assertTrue(node.isTextual)
              case _ => fail("Result serialization failed")
            }
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "newPublicKey") ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], SECRET_NOT_ADDED().apiCode)
      }
    }

    "reply at /allPublicKeys" in {
      Post(basePath + "allPublicKeys")
        .withEntity(
          SerializationUtil.serialize(ReqAllPropositionsPost(None))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.findValues("propositions").size())
            result.get("propositions") match{
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
          SerializationUtil.serialize(ReqAllPropositionsPost(Some("proptype")))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

  }

}

package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import com.horizen.api.http.SidechainBlockRestSchema._
import com.horizen.api.http.SidechainBlockErrorResponse._
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import scorex.core.bytesToId
import scorex.util.ModifierId

import scala.collection.JavaConverters._

class SidechainBlockApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/block/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainBlockApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "findById") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findById").withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findById") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "findLastIds") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findLastIds").withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findLastIds") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "findIdByHeight") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findIdByHeight").withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findIdByHeight") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "submit") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "submit").withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "submit") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "generate") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "generate").withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "generate") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /findById" in {
      val invalid_block_id_lenght_json = "{\"blockId\": \"invalid_block_id_length\"}"
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById("valid_block_id_0000000000000000000000000000000000000000000000000"))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(2, result.elements().asScala.length)
            assertTrue(result.get("blockHex").isTextual)
            assertTrue(result.get("block").isObject)
            jsonChecker.assertsOnBlockJson(result.get("block"), genesisBlock)
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      Post(basePath + "findById")
        .withEntity(invalid_block_id_lenght_json) ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findById")
        .withEntity(invalid_block_id_lenght_json) ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      sidechainApiMockConfiguration.setShould_history_getBlockById_return_value(false)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById("invalid_block_id_00000000000000000000000000000000000000000000000"))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorInvalidBlockId("", None).code)
      }
    }

    "reply at /findLastIds" in {
      Post(basePath + "findLastIds")
        .withEntity("{\"number\": -1}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findLastIds")
        .withEntity("{\"number\": -1}") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "findLastIds")
        .withEntity("{\"number\": 0}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findLastIds")
        .withEntity("{\"number\": a_number}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findLastIds")
        .withEntity("{\"number\": 0}") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "findLastIds")
        .withEntity(SerializationUtil.serialize(ReqLastIds(1))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("lastBlockIds").isArray)
            assertEquals(3, result.get("lastBlockIds").elements().asScala.length)
            val elems: Array[String] = result.get("lastBlockIds").elements().asScala.map(node => {
              assertTrue(node.isTextual)
              node.asText()
            }).toArray
            val expected: Array[String] = Array("block_id_1", "block_id_2", "block_id_3")
            elems shouldEqual expected
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
    }

    "reply at /findIdByHeight" in {
      Post(basePath + "findIdByHeight")
        .withEntity("{\"height\": -1}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findIdByHeight")
        .withEntity("{\"height\": -1}") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "findIdByHeight")
        .withEntity("{\"height\": 0}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findIdByHeight")
        .withEntity("{\"height\": an_height}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findIdByHeight")
        .withEntity("{\"height\": 0}") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "findIdByHeight")
        .withEntity(SerializationUtil.serialize(ReqFindIdByHeight(1))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("blockId").isTextual)
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      sidechainApiMockConfiguration.setShould_history_getBlockIdByHeight_return_value(false)
      Post(basePath + "findIdByHeight")
        .withEntity(SerializationUtil.serialize(ReqFindIdByHeight(20))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorInvalidBlockHeight("", None).code)
      }
    }

    "reply at /best" in {
      Post(basePath + "best") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(2, result.elements().asScala.length)
            assertTrue(result.get("height").isInt)
            assertEquals(230, result.get("height").asInt())
            assertTrue(result.get("block").isObject)
            jsonChecker.assertsOnBlockJson(result.get("block"), genesisBlock)
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      sidechainApiMockConfiguration.setShould_history_getCurrentHeight_return_value(false)
      Post(basePath + "best") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorInvalidBlockHeight("", None).code)
      }
    }

    "reply at /template" in {
      Post(basePath + "template") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(2, result.elements().asScala.length)
            assertTrue(result.get("blockHex").isTextual)
            assertTrue(result.get("block").isObject)
            jsonChecker.assertsOnBlockJson(result.get("block"), genesisBlock)
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      sidechainApiMockConfiguration.setShould_forger_TryGetBlockTemplate_reply(false)
      Post(basePath + "template") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBlockTemplate("", None).code)
      }
    }

    "reply at /submit" in {
      Post(basePath + "submit")
        .withEntity(SerializationUtil.serialize(ReqSubmit("0000b82c"))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("blockId").isTextual)
            assertEquals(BytesUtils.toHexString(scorex.util.idToBytes(genesisBlock.id)), result.get("blockId").asText())
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      Post(basePath + "submit")
        .withEntity(SerializationUtil.serialize(ReqSubmit("not_accepted_block_hex"))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBlockNotAccepted("", None).code)
      }
      sidechainApiMockConfiguration.setShould_blockActor_SubmitSidechainBlock_reply(false)
      Post(basePath + "submit")
        .withEntity(SerializationUtil.serialize(ReqSubmit("0000b82c"))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBlockNotAccepted("", None).code)
      }
      Post(basePath + "submit") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "submit")
        .withEntity(SerializationUtil.serialize("{blockHex=\"\"}")) ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /generate" in {
      Post(basePath + "generate")
        .withEntity("{\"number\": -1}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "generate")
        .withEntity("{\"number\": -1}") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "generate")
        .withEntity("{\"number\": 0}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "generate")
        .withEntity("{\"number\": a_number}") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "generate")
        .withEntity("{\"number\": 0}") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "generate")
        .withEntity(SerializationUtil.serialize(ReqGenerate(4))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("blockIds").isArray)
            assertEquals(4, result.get("blockIds").elements().asScala.length)
            val elems: Array[String] = result.get("blockIds").elements().asScala.map(node => {
              assertTrue(node.isTextual)
              node.asText()
            }).toArray
            val expected: Array[ModifierId] = Array(
              bytesToId("block_id_1".getBytes),
              bytesToId("block_id_2".getBytes),
              bytesToId("block_id_3".getBytes),
              bytesToId("block_id_4".getBytes))
            elems shouldEqual expected
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
      sidechainApiMockConfiguration.setShould_blockActor_GenerateSidechainBlocks_reply(false)
      Post(basePath + "generate")
        .withEntity(SerializationUtil.serialize(ReqGenerate(4))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBlockNotCreated("", None).code)
      }
    }
  }
}
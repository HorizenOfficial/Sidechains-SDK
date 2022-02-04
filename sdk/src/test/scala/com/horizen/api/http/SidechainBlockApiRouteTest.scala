package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.SidechainBlockErrorResponse._
import com.horizen.api.http.SidechainBlockRestSchema._
import com.horizen.consensus.{ConsensusEpochAndSlot, intToConsensusEpochNumber, intToConsensusSlotNumber}
import com.horizen.forge
import com.horizen.serialization.SerializationUtil
import org.junit.Assert._
import scorex.util.bytesToId

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}
import java.util.{Optional => JOptional}

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
    }

    "reply at /findById" in {
      val invalid_block_id_lenght_json = "{\"blockId\": \"invalid_block_id_length\"}"
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById("valid_block_id_0000000000000000000000000000000000000000000000000"))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(3, result.elements().asScala.length)
        assertTrue(result.get("blockHex").isTextual)
        assertTrue(result.get("block").isObject)
        assertTrue(result.get("height").isInt)
        jsonChecker.assertsOnBlockJson(result.get("block"), genesisBlock)
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
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorInvalidBlockId("", JOptional.empty()).code)
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
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertTrue(result.get("lastBlockIds").isArray)
        assertEquals(3, result.get("lastBlockIds").elements().asScala.length)
        val elems: Array[String] = result.get("lastBlockIds").elements().asScala.map(node => {
          assertTrue(node.isTextual)
          node.asText()
        }).toArray
        val expected: Array[String] = Array("block_id_1", "block_id_2", "block_id_3")
        elems shouldEqual expected
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
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("blockId").isTextual)
      }
      sidechainApiMockConfiguration.setShould_history_getBlockIdByHeight_return_value(false)
      Post(basePath + "findIdByHeight")
        .withEntity(SerializationUtil.serialize(ReqFindIdByHeight(20))) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorInvalidBlockHeight("", JOptional.empty()).code)
      }
    }

    "reply at /best" in {
      Post(basePath + "best") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(2, result.elements().asScala.length)
        assertTrue(result.get("height").isInt)
        assertEquals(230, result.get("height").asInt())
        assertTrue(result.get("block").isObject)
        jsonChecker.assertsOnBlockJson(result.get("block"), genesisBlock)
      }
      sidechainApiMockConfiguration.setShould_history_getCurrentHeight_return_value(false)
      Post(basePath + "best") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorInvalidBlockHeight("", JOptional.empty()).code)
      }
    }

    "Successfully reply at /stopForging" in {
      sidechainApiMockConfiguration.should_blockActor_StopForging_reply = true

      Post(basePath + "stopForging") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "Failed reply at /stopForging" in {
      sidechainApiMockConfiguration.should_blockActor_StopForging_reply = false

      Post(basePath + "stopForging") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorStopForging("", JOptional.empty()).code)
      }
    }

    "Successfully reply at /startForging" in {
      sidechainApiMockConfiguration.should_blockActor_StartForging_reply = true

      Post(basePath + "startForging") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "Failed reply at /startForging" in {
      sidechainApiMockConfiguration.should_blockActor_StartForging_reply = false

      Post(basePath + "startForging") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorStartForging("", JOptional.empty()).code)
      }
    }

    "Successfully reply at /generate (2,1)" in {
      val successBlockId = bytesToId("firstBlock".getBytes())
      sidechainApiMockConfiguration.blockActor_ForgingEpochAndSlot_reply.put(
        ConsensusEpochAndSlot(intToConsensusEpochNumber(2), intToConsensusSlotNumber(1)), Success(successBlockId))

      Post(basePath + "generate").withEntity("{\"epochNumber\": 2, \"slotNumber\": 1}") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("blockId").isTextual)
        val blockId = result.get("blockId").asText()
        assertEquals(blockId, successBlockId)
      }
    }

    "Success forge reply with failed future at /generate (2,2)" in {
      sidechainApiMockConfiguration.blockActor_ForgingEpochAndSlot_reply.put(
        ConsensusEpochAndSlot(intToConsensusEpochNumber(2), intToConsensusSlotNumber(2)), Failure(new IllegalArgumentException))

      Post(basePath + "generate").withEntity("{\"epochNumber\": 2, \"slotNumber\": 2}") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBlockNotCreated("", JOptional.empty()).code)
      }
    }

    "Failed forge reply at /generate (2,3)" in {
      Post(basePath + "generate").withEntity("{\"epochNumber\": 2, \"slotNumber\": 3}") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue //@TODO fix it, to avoid Internal Server error?
      }
    }

    "Successfully reply at /forgingInfo" in {
      val expectedConsensusSecondsInSlot = 1000
      val expectedConsensusSlotsInEpoch = 60
      val expectedEpochNumber = intToConsensusEpochNumber(5)
      val expectedSlotNumber = intToConsensusSlotNumber(6)
      val expectedBestEpochAndSlot = ConsensusEpochAndSlot(expectedEpochNumber, expectedSlotNumber)
      val expectedForgingEnabled = true

      sidechainApiMockConfiguration.should_blockActor_ForgingInfo_reply =
        Success(forge.ForgingInfo(expectedConsensusSecondsInSlot, expectedConsensusSlotsInEpoch, expectedBestEpochAndSlot, expectedForgingEnabled))

      Post(basePath + "forgingInfo") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result =  mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(5, result.elements().asScala.length)
        assertEquals(expectedConsensusSecondsInSlot, result.get("consensusSecondsInSlot").asInt())
        assertEquals(expectedConsensusSlotsInEpoch, result.get("consensusSlotsInEpoch").asInt())
        assertEquals(expectedEpochNumber, result.get("bestEpochNumber").asInt())
        assertEquals(expectedSlotNumber, result.get("bestSlotNumber").asInt())
        assertEquals(expectedForgingEnabled, result.get("forgingEnabled").asBoolean())
      }
    }

    "Failed reply at /forgingInfo" in {
      sidechainApiMockConfiguration.should_blockActor_ForgingInfo_reply = Failure(new IllegalArgumentException)

      Post(basePath + "forgingInfo") ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorGetForgingInfo("", JOptional.empty()).code)
      }
    }
  }
}
package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.MainchainErrorResponse.{ErrorMainchainBlockNotFound, ErrorMainchainBlockReferenceNotFound, ErrorMainchainInvalidParameter}
import com.horizen.api.http.MainchainRestSchema.{ReqBlockBy, ReqBlockInfoBy}
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert._

import scala.collection.JavaConverters._

class MainchainBlockApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/mainchain/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> mainchainBlockApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "blockReferenceInfoBy").withEntity("maybe_a_json") ~> mainchainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "blockReferenceInfoBy").withEntity("maybe_a_json") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "blockReferenceByHash").withEntity("maybe_a_json") ~> mainchainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "blockReferenceByHash").withEntity("maybe_a_json") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /bestBlockReferenceInfo" in {
      Post(basePath + "bestBlockReferenceInfo") ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnMainchainApiResponse(entityAs[String], mainchainBlockReferenceInfoRef)
      }
      sidechainApiMockConfiguration.setShould_history_getBestMainchainBlockReferenceInfo_return_value(false)
      Post(basePath + "bestBlockReferenceInfo") ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorMainchainBlockNotFound("", None).code)
      }
    }

    "reply at /genesisBlockReferenceInfo" in {
      Post(basePath + "genesisBlockReferenceInfo") ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnMainchainApiResponse(entityAs[String], mainchainBlockReferenceInfoRef)
      }
      sidechainApiMockConfiguration.setShould_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value(false)
      Post(basePath + "genesisBlockReferenceInfo") ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorMainchainBlockNotFound("", None).code)
      }
    }

    "reply at /blockReferenceInfoBy" in {
      sidechainApiMockConfiguration.setShould_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value(true)
      // blockReferenceInfoBy hash
      // parameter 'format' = false
      Post(basePath + "blockReferenceInfoBy")
        .withEntity(SerializationUtil.serialize(ReqBlockInfoBy(Some("AABBCC"), None))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("blockHex").isTextual)
          case _ => fail("Serialization failed for object MainchainApiResponse")
        }
      }
      // blockReferenceInfoBy hash
      // parameter 'format' = true
      Post(basePath + "blockReferenceInfoBy")
        .withEntity(SerializationUtil.serialize(ReqBlockInfoBy(Some("AABBCC"), None, true))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnMainchainApiResponse(entityAs[String], mainchainBlockReferenceInfoRef)
      }
      // blockReferenceInfoBy height
      // parameter 'format' = false
      Post(basePath + "blockReferenceInfoBy")
        .withEntity(SerializationUtil.serialize(ReqBlockInfoBy(None, Some(300)))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("blockHex").isTextual)
          case _ => fail("Serialization failed for object MainchainApiResponse")
        }
      }
      // blockReferenceInfoBy height
      // parameter 'format' = true
      Post(basePath + "blockReferenceInfoBy")
        .withEntity(SerializationUtil.serialize(ReqBlockInfoBy(None, Some(300), true))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnMainchainApiResponse(entityAs[String], mainchainBlockReferenceInfoRef)
      }
      // blockReferenceInfoBy hash but no result
      sidechainApiMockConfiguration.setShould_history_getMainchainBlockReferenceInfoByHash_return_value(false)
      Post(basePath + "blockReferenceInfoBy")
        .withEntity(SerializationUtil.serialize(ReqBlockInfoBy(Some("AABBCC"), None))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorMainchainBlockReferenceNotFound("", None).code)
      }
      // blockReferenceInfoBy height but no result
      sidechainApiMockConfiguration.setShould_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value(false)
      Post(basePath + "blockReferenceInfoBy")
        .withEntity(SerializationUtil.serialize(ReqBlockInfoBy(None, Some(300)))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorMainchainBlockReferenceNotFound("", None).code)
      }
      // blockReferenceInfoBy invalid parameters
      Post(basePath + "blockReferenceInfoBy") ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorMainchainInvalidParameter("", None).code)
      }
    }

    "reply at /blockReferenceByHash" in {
      // parameter 'format' = false
      Post(basePath + "blockReferenceByHash")
        .withEntity((SerializationUtil.serialize(ReqBlockBy("AABBCC")))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("blockHex").isTextual)
          case _ => fail("Serialization failed for object MainchainBlockResponse")
        }
      }
      // parameter 'format' = true
      Post(basePath + "blockReferenceByHash")
        .withEntity((SerializationUtil.serialize(ReqBlockBy("AABBCC", true)))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      sidechainApiMockConfiguration.setShould_history_getMainchainBlockReferenceByHash_return_value(false)
      Post(basePath + "blockReferenceByHash")
        .withEntity((SerializationUtil.serialize(ReqBlockBy("AABBCC")))) ~> mainchainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorMainchainBlockNotFound("", None).code)
      }
    }
  }

  private def assertsOnMainchainApiResponse(json: String, mcRef: MainchainBlockReferenceInfo): Unit = {
    mapper.readTree(json).get("result") match {
      case result =>
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("blockReferenceInfo").isObject)
        val node = result.get("blockReferenceInfo")
        assertEquals(5, node.elements().asScala.length)
        assertTrue(node.get("hash").isTextual)
        assertTrue(node.get("parentHash").isTextual)
        assertTrue(node.get("height").isInt)
        assertTrue(node.get("mainchainHeaderSidechainBlockId").isTextual)
        assertTrue(node.get("mainchainReferenceDataSidechainBlockId").isTextual)

        assertEquals(node.get("hash").textValue(), BytesUtils.toHexString(mcRef.getMainchainHeaderHash))
        assertEquals(node.get("parentHash").textValue(), BytesUtils.toHexString(mcRef.getParentMainchainHeaderHash))
        assertEquals(node.get("height").asInt(), mcRef.getMainchainHeight)
        assertEquals(node.get("mainchainHeaderSidechainBlockId").textValue(), BytesUtils.toHexString(mcRef.getMainchainHeaderSidechainBlockId))
        assertEquals(node.get("mainchainReferenceDataSidechainBlockId").textValue(), BytesUtils.toHexString(mcRef.getMainchainReferenceDataSidechainBlockId))
      case _ => fail("Serialization failed for object MainchainApiResponse")
    }
  }
}
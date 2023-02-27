package com.horizen.utxo.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.SidechainApiRouteTest
import com.horizen.fixtures.BoxFixture
import com.horizen.params.MainNetParams
import com.horizen.utils.BytesUtils
import org.junit.Assert._

import scala.collection.JavaConverters._

class SidechainCswApiRouteTest extends SidechainApiRouteTest with BoxFixture {

  override val basePath = "/csw/"
  val mcAddress = "ABCDEABCDEABCDEABCDEABCDEABCDEABCDE"

  "The Api" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainCswApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

     Post(basePath + "hasCeased").withEntity("maybe_a_json") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "hasCeased").withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "isCSWEnabled").withEntity("maybe_a_json") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "isCSWEnabled").withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "generateCswProof").withEntity("maybe_a_json") ~> sidechainCswApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "generateCswProof").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "generateCswProof").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "cswInfo").withEntity("maybe_a_json") ~> sidechainCswApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "cswInfo").withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "cswBoxIds").withEntity("maybe_a_json") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "cswBoxIds").withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "nullifier").withEntity("maybe_a_json") ~> sidechainCswApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "nullifier").withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /hasCeased" in {
      Post(basePath + "hasCeased") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("state").isBoolean)
        assertTrue(result.get("state").asBoolean)
      }
      //Testing response in case of internal error
      sidechainApiMockConfiguration.setShould_nodeViewHolder_GetDataFromCurrentNodeView_reply(false)
      Post(basePath + "hasCeased") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object ErrorRetrievingCeasingState")
        assertEquals(3, result.elements().asScala.length)
        assertTrue(result.get("code").isTextual)
        assertEquals("0701", result.get("code").asText())
      }

    }

    "reply at /isCSWEnabled" in {
      Post(basePath + "isCSWEnabled") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("cswEnabled").isBoolean)
        assertTrue(result.get("cswEnabled").asBoolean)
      }
    }

    "reply at /cswBoxIds" in {
      Post(basePath + "cswBoxIds") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object sidechainCswApiRoute")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("cswBoxIds").isArray)
        assertEquals(3, result.get("cswBoxIds").elements().asScala.length)
        val boxIds = result.get("cswBoxIds").elements().asScala.toList
        assertEquals("1111", boxIds(0).asText())
        assertEquals("2222", boxIds(1).asText())
        assertEquals("3333", boxIds(2).asText())
      }
    }

    "reply at /cswInfo" in {
      Post(basePath + "cswInfo")
        .withEntity("{\"boxId\":\"" + BytesUtils.toHexString(getRandomBoxId(0)) + "\"}") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object sidechainCswApiRoute")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("cswInfo").isObject)
        val cswInfoJson = result.get("cswInfo")
        assertTrue(cswInfoJson.get("cswType").isTextual)
        assertEquals(cswInfoJson.get("cswType").asText(), "UtxoCswData")
        assertTrue(cswInfoJson.get("amount").isInt)
        assertEquals(cswInfoJson.get("amount").asInt(), 42)
        assertTrue(cswInfoJson.get("scId").isTextual)
        assertEquals(cswInfoJson.get("scId").asText().toUpperCase, "ABCD")
        assertTrue(cswInfoJson.get("nullifier").isTextual)
        assertEquals(cswInfoJson.get("nullifier").asText().toUpperCase, "FFFF")
        assertTrue(cswInfoJson.get("activeCertData").isTextual)
        assertEquals(cswInfoJson.get("activeCertData").asText().toUpperCase, "BBBB")
        assertTrue(cswInfoJson.get("ceasingCumScTxCommTree").isTextual)
        assertEquals(cswInfoJson.get("ceasingCumScTxCommTree").asText().toUpperCase, "CCCC")

        assertTrue(cswInfoJson.get("proofInfo").isObject)
        val proofInfo = cswInfoJson.get("proofInfo")
        assertEquals(3, proofInfo.elements().asScala.length)
        assertTrue(proofInfo.get("status").isTextual)
        assertEquals(proofInfo.get("status").asText(), "Absent")
        assertTrue(proofInfo.get("scProof").isTextual)
        assertEquals(proofInfo.get("scProof").asText().toUpperCase, "FBFB")
        assertTrue(proofInfo.get("receiverAddress").isTextual)
        assertEquals(proofInfo.get("receiverAddress").asText(), "SomeDestination")
      }
    }

    "reply at /nullifier" in {
      Post(basePath + "nullifier")
        .withEntity("{\"boxId\":\"" + BytesUtils.toHexString(getRandomBoxId(0)) + "\"}") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object sidechainCswApiRoute")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("nullifier").isTextual)
        assertEquals(result.get("nullifier").asText().toUpperCase, "FAFA")
      }
    }

    "reply at /generateCswProof" in {
      Post(basePath + "generateCswProof")
        .addCredentials(credentials).withEntity("{\"boxId\":\"" + BytesUtils.toHexString(getRandomBoxId(0)) + "\", \"receiverAddress\":\"" + mcAddress + "\"}") ~> sidechainCswApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object sidechainCswApiRoute")

        assertEquals(2, result.elements().asScala.length)

        assertTrue(result.get("state").isTextual)
        assertEquals(result.get("state").asText(), "ProofCreationFinished")
        assertTrue(result.get("description").isTextual)
        assertEquals(result.get("description").asText(), "CSW proof generation is finished")
      }
    }
  }

  "When CSW is disabled, the Api" should {
    val params = MainNetParams(isCSWEnabled = false)
    val sidechainCswApiRouteWithDisabledCSW = SidechainCswApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedCswManagerActorRef,params).route

    "reply at /hasCeased" in {
      sidechainApiMockConfiguration.setShould_nodeViewHolder_GetDataFromCurrentNodeView_reply(true)
      Post(basePath + "hasCeased") ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("state").isBoolean)
        assertTrue(result.get("state").asBoolean)
      }
    }

    "reply at /isCSWEnabled" in {
      Post(basePath + "isCSWEnabled") ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("cswEnabled").isBoolean)
        assertFalse(result.get("cswEnabled").asBoolean)
      }
    }

    def checkIsNotImplemented(): Unit = {
      status.intValue() shouldBe StatusCodes.OK.intValue
      responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      val result = mapper.readTree(entityAs[String]).get("error")
      if (result == null)
        fail("Serialization failed for object ErrorCSWNotEnabled")

      assertEquals(3, result.elements().asScala.length)
      assertTrue(result.get("code").isTextual)
      assertEquals("0707", result.get("code").asText())
    }

    "reply that it is not implemented at /cswBoxIds" in {
      Post(basePath + "cswBoxIds") ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        checkIsNotImplemented()
      }
    }

    "reply that it is not implemented at /cswInfo" in {
      Post(basePath + "cswInfo")
        .withEntity("{\"boxId\":\"" + BytesUtils.toHexString(getRandomBoxId(0)) + "\"}") ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        checkIsNotImplemented()
      }
    }

    "reply that it is not implemented at /nullifier" in {
      Post(basePath + "nullifier")
        .withEntity("{\"boxId\":\"" + BytesUtils.toHexString(getRandomBoxId(0)) + "\"}") ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        checkIsNotImplemented()
      }
    }


    "reply that it is not implemented at /generateCswProof" in {
      Post(basePath + "generateCswProof")
        .withEntity("{\"boxId\":\"" + BytesUtils.toHexString(getRandomBoxId(0)) + "\", \"receiverAddress\":\"" + mcAddress + "\"}") ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        checkIsNotImplemented()
      }
    }

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainCswApiRouteWithDisabledCSW ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }

      Get(basePath) ~> Route.seal(sidechainCswApiRouteWithDisabledCSW) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }


      Post(basePath + "randomMethodName").withEntity("maybe_a_json") ~> Route.seal(sidechainCswApiRouteWithDisabledCSW) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

    }



  }

}

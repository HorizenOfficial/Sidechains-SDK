package io.horizen.api.http.route

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import io.horizen.json.SerializationUtil
import io.horizen.utxo.api.http.SimpleCustomApi
import org.junit.Assert.{assertEquals, assertTrue}

import scala.collection.JavaConverters._

class SidechainApplicationApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/customSecret/"

  "The Api should to" should {

    "reject and reply with http error" in {

      Get(basePath) ~> Route.seal(applicationApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /getAllSecretByEmptyHttpBody" in {

      Post(basePath + "getAllSecretByEmptyHttpBody") ~> applicationApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("secrets").isArray)
        assertEquals(2, result.get("secrets").elements().asScala.length)
        result.get("secrets").elements().asScala.toList.foreach(node => {
          assertTrue(node.isObject)
          assertEquals(4, node.elements().asScala.length)
        })
      }
    }

    //TODO we use different JSON serialization marshaller in test class and in the real application. Rewrite SidechainApiRouteTest to align to real application
    "reply at /getNSecretOtherImplementation" ignore {
      println(SerializationUtil.serialize(new SimpleCustomApi.GetSecretRequest(2)))
      val jsonBody = "{\"secretCount\" : \"2\"}"
      Post(basePath + "getNSecretOtherImplementation").withEntity(ContentTypes.`application/json`, SerializationUtil.serialize(jsonBody)) ~> applicationApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("secrets").isArray)
        assertEquals(2, result.get("secrets").elements().asScala.length)
        result.get("secrets").elements().asScala.toList.foreach(node => {
          assertTrue(node.isObject)
          assertEquals(0, node.elements().asScala.length)
        })
      }
    }
  }

}

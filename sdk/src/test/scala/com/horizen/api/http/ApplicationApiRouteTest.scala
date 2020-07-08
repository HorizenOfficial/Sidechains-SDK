package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.junit.Assert.{assertEquals, assertTrue}
import scala.collection.JavaConverters._

class ApplicationApiRouteTest extends SidechainApiRouteTest {

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
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("secrets").isArray)
            assertEquals(2, result.get("secrets").elements().asScala.length)
            result.get("secrets").elements().asScala.toList.foreach(node => {
              assertTrue(node.isObject)
              assertEquals(0, node.elements().asScala.length)
            })
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
    }
  }
}

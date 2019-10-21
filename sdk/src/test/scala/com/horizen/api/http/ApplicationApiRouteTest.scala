package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.junit.Assert.{assertEquals, assertTrue}
import scala.collection.JavaConverters._

class ApplicationApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/simpleApi/"

  "The Api should to" should {

    "reject and reply with http error" in {

      Get(basePath) ~> Route.seal(applicationApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "allPublicKeys") ~> Route.seal(applicationApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], new ErrorAllPublickeys("", None).code())
      }

      sidechainApiMockConfiguration.setUseExtendedNodeView(true)
      Post(basePath + "allPublicKeys") ~> applicationApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        mapper.readTree(entityAs[String]).get("result") match {
          case result =>
            assertEquals(1, result.elements().asScala.length)
            assertTrue(result.get("propositions").isArray)
            assertEquals(3, result.get("propositions").elements().asScala.length)
            val elems: Array[String] = result.get("propositions").elements().asScala.map(node => {
              assertTrue(node.isObject)
              val pbk = node.get("publicKey")
              assertTrue(pbk.isTextual)
              pbk.asText()
            }).toArray
            val expected: Array[String] = Array("publicKey_1", "publicKey_2", "publicKey_3")
            elems shouldEqual expected
          case _ => fail("Serialization failed for object SidechainApiResponseBody")
        }
      }
    }
  }
}

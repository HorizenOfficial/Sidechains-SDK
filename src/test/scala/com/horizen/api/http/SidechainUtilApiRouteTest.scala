package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}

class SidechainUtilApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/util/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainUtilApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainUtilApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

  }
}

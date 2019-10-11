package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MethodRejection, Route}

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
    }

  }
}

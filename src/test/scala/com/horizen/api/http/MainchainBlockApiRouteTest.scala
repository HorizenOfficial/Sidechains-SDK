package com.horizen.api.http

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}

class MainchainBlockApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/mainchain/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> mainchainBlockApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
      }
    }

    "reply at /getBestMainchainBlockReferenceInfo" in {
      Post(basePath + "getBestMainchainBlockReferenceInfo") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
      }
    }

    "reply at /getMainchainBlockReference" in {
      Post(basePath + "getMainchainBlockReference") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
      }
    }

    "reply at /createMainchainBlockReference" in {
      Post(basePath + "createMainchainBlockReference") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
      }
    }

  }
}

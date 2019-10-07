package com.horizen.api.http

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}

class MainchainBlockApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/mainchain/"

  "The API" should {

    "reject and reply with http error" in {
      Get(basePath) ~> mainchainBlockApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
    }

    "reply OK at /getBestMainchainBlockReferenceInfo" in {
      Post(basePath + "getBestMainchainBlockReferenceInfo") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "reply OK at /getMainchainBlockReference" in {
      Post(basePath + "getMainchainBlockReference") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "reply OK at /createMainchainBlockReference" in {
      Post(basePath + "createMainchainBlockReference") ~> Route.seal(mainchainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

  }
}

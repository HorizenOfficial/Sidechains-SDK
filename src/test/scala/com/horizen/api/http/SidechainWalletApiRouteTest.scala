package com.horizen.api.http

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}

class SidechainWalletApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/wallet/"

  "The API" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainWalletApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
      Post(basePath + "allBoxes") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allBoxes") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "balance") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "balance") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "allPublicKeys") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allPublicKeys") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

}

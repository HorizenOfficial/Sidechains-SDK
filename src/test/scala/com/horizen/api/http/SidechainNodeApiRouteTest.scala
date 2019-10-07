package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}

class SidechainNodeApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/node/"

  "The API" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainNodeApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
      Post(basePath + "connect") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "connect") ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }
}

package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}

class SidechainNodeApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/node/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainNodeApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
      }

      Post(basePath + "connect") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "connect").withEntity("maybe_a_json") ~> sidechainNodeApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "connect") ~> Route.seal(sidechainNodeApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
      }
    }

  }
}

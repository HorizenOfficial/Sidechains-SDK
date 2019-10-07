package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}

class SidechainBlockApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/block/"

  "The API" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainBlockApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
      Post(basePath + "findById") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findById") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "findLastIds") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findLastIds") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "findIdByHeight") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findIdByHeight") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "submit") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "submit") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "generate") ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "generate") ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

}

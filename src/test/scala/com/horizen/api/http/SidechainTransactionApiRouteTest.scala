package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}

class SidechainTransactionApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/transaction/"

  "The API" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainTransactionApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
      Post(basePath + "allTransactions") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allTransactions") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "findById") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findById") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "decodeTransactionBytes") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "decodeTransactionBytes") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "createRegularTransaction") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createRegularTransaction") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "createRegularTransactionSimplified") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createRegularTransactionSimplified") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "sendCoinsToAddress") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "sendCoinsToAddress") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(basePath + "sendTransaction") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "sendTransaction") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }
}

package io.horizen.account.api.http.route

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import org.scalatest.prop.TableDrivenPropertyChecks

class AccountBlockApiRouteTest extends AccountSidechainApiRouteTest with TableDrivenPropertyChecks {
  override val basePath = "/block/"

  "The Api should" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainBlockApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainBlockApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      val cases = Table(
        ("Route", "Expected response code"),
        ("findById", StatusCodes.BadRequest.intValue),
        ("findLastIds", StatusCodes.BadRequest.intValue),
        ("findIdByHeight", StatusCodes.BadRequest.intValue),
        ("getBestBlockInfo", StatusCodes.NotFound.intValue),
        ("findBlockInfoById", StatusCodes.BadRequest.intValue),
        ("getFeePayments", StatusCodes.BadRequest.intValue),
        ("getForwardTransfers", StatusCodes.BadRequest.intValue),
        ("startForging", StatusCodes.OK.intValue),
        ("stopForging", StatusCodes.OK.intValue),
        ("generateBlockForEpochNumberAndSlot", StatusCodes.NotFound.intValue),
        ("getForgingInfo", StatusCodes.NotFound.intValue)
      )

      forAll(cases) { (route, expectedCode) =>
        val path = basePath + route

        if (expectedCode == StatusCodes.BadRequest.intValue) {
          Post(path).addCredentials(credentials) ~> sidechainBlockApiRoute ~> check {
            rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
          }
          Post(path).addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
            rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
          }
        }

        Post(path).addCredentials(credentials) ~> Route.seal(sidechainBlockApiRoute) ~> check {
          status.intValue() shouldBe expectedCode
          if (expectedCode != StatusCodes.InternalServerError.intValue && expectedCode != StatusCodes.NotFound.intValue) {
            responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
          }
        }

        if (expectedCode != StatusCodes.NotFound.intValue && route != "startForging" && route != "stopForging") {
          Post(path).addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainBlockApiRoute ~> check {
            rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
          }
        }
      }
    }

    "reply at /getForwardTransfers" in {
      val path = basePath + "getForwardTransfers"
      var json = """{"blockId": "0000000000000000000000000000000000000000000000000000000000000000"}"""
      Post(path).addCredentials(credentials).withEntity(json) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      json = """{"blockId": "123"}"""
      Post(path).addCredentials(credentials).withEntity(json) ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
    }

    "reply at /getFeePayments" in {
      val path = basePath + "getFeePayments"
      var json = """{"blockId": "0000000000000000000000000000000000000000000000000000000000000000"}"""
      Post(path).addCredentials(credentials).withEntity(json) ~> sidechainBlockApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      json = """{"blockId": "123"}"""
      Post(path).addCredentials(credentials).withEntity(json) ~> sidechainBlockApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
    }
  }
}

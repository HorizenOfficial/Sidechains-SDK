package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server._
import Directives._

class SidechainRejectionApiRouteTest extends SidechainApiRouteTest {

  override val basePath: String = "/wallet/"

  "The Api" should {

    "reply at /coinsBalance" in {
      Post(basePath + "coinsBalance").withHeaders(apiTokenHeader) ~> (sidechainWalletApiRoute ~ walletCoinsBalanceApiRejected) ~> check {
        response shouldEqual ((Post(basePath + "coinsBalance").withHeaders(apiTokenHeader) ~> sidechainWalletApiRoute).response)
      }
    }

    "reject and reply with http error" in {
      Post(basePath + "coinsBalance").withHeaders(apiTokenHeader) ~> Route.seal({
        walletCoinsBalanceApiRejected ~ sidechainWalletApiRoute
      }) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "coinsBalance") ~> Route.seal({
        walletApiRejected ~ sidechainWalletApiRoute
      }) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath) ~> Route.seal({
        walletApiRejected ~ sidechainWalletApiRoute
      }) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }
  }
}

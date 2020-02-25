package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server._
import Directives._

class SidechainRejectionApiRouteTest extends SidechainApiRouteTest {

  override val basePath: String = "/wallet/"

  "The Api should to" should {

    "reply at /balance" in {
      Post(basePath + "balance") ~> (sidechainWalletApiRoute ~ walletBalanceApiRejected) ~> check {
        response shouldEqual ((Post(basePath + "balance") ~> sidechainWalletApiRoute).response)
      }
    }

    "reject and reply with http error" in {
      Post(basePath + "balance") ~> Route.seal({
        walletBalanceApiRejected ~ sidechainWalletApiRoute
      }) ~> check {
        status.intValue() shouldBe StatusCodes.NotFound.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "balance") ~> Route.seal({
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

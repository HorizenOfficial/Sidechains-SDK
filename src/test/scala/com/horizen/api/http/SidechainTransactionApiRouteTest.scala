package com.horizen.api.http

import com.horizen.fixtures.SidechainNodeViewHolderFixture

import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.testkit.JUnitRouteTest
import akka.http.javadsl.testkit.TestRoute

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitSuite
import scorex.core.settings.ScorexSettings
import scorex.util.ModifierId

class SidechainTransactionApiRouteTest
  extends JUnitSuite
  with SidechainNodeViewHolderFixture
{

  val getMemoryPoolRoute = getSidechainTransactionApiRoute.allTransactions

  @Test
  def testMemoryPool() : Unit = {
    System.out.println("Testing getMemoryPool...")
    //val request = Post(uri = "transaction/getMemoryPool")
  }

}

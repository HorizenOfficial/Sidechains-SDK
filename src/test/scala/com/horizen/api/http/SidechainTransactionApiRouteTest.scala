package com.horizen.api.http

import com.horizen.fixtures.SidechainNodeViewHolderFixture

import org.junit.Test
import org.scalatest.junit.JUnitSuite

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

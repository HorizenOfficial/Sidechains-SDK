package io.horizen.account.dump

import org.junit.Test

class DumpTest {

  @Test
  def testDump(): Unit = {

    new DumpService().doDump("/zendrive/eondata/data_mainnet/blockchain");

  }

}

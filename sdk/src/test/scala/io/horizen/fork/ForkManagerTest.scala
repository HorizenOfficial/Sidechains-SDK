package io.horizen.fork

import org.junit.Assert.{assertNotNull, assertNull, assertTrue}
import org.junit.{Before, Test}
import org.scalatestplus.junit.JUnitSuite

import scala.util.Try

class ForkManagerTest extends JUnitSuite {

  @Before
  def init(): Unit = {
    ForkManager.reset()
  }

  @Test
  def ForkManagerTest(): Unit = {
    val simpleForkConfigurator = new SimpleForkConfigurator

    var res = Try.apply(ForkManager.init(simpleForkConfigurator, "wrongname"))
    assertTrue("Expected failure on ForkManager initialization", res.isFailure)

    res = Try.apply(ForkManager.init(new BadForkConfigurator, "regtest"))
    assertTrue("Expected failure on ForkManager initialization", res.isFailure)

    res = Try.apply(ForkManager.init(simpleForkConfigurator, "regtest"))
    assertTrue("Expected successed ForkManager initialization", res.isSuccess)

    res = Try.apply(ForkManager.init(simpleForkConfigurator, "regtest"))
    assertTrue("Expected failure on ForkManager initialization", res.isFailure)

    val mainchainFork1 = ForkManager.getMainchainFork(419)
    assertNull("Expected not to get mainchain fork", mainchainFork1)

    val mainchainFork2 = ForkManager.getMainchainFork(420)
    assertNotNull("Expected to get mainchain fork", mainchainFork2)

    val sidechainConsensusFork1 = ForkManager.getSidechainFork(-5)
    assertNull("Expected not to get sidechain fork", sidechainConsensusFork1)

    val sidechainConsensusFork2 = ForkManager.getSidechainFork(9)
    assertNotNull("Expected to get sidechain fork", sidechainConsensusFork2)
    assertTrue("Expected not to get sidechain fork", sidechainConsensusFork2.isInstanceOf[MandatorySidechainFork])

    val sidechainConsensusFork3 = ForkManager.getSidechainFork(10)
    assertNotNull("Expected to get sidechain fork", sidechainConsensusFork3)
    assertTrue("Expected not to get sidechain fork", sidechainConsensusFork3.isInstanceOf[SidechainFork1])

    val sidechainConsensusFork4 = ForkManager.getSidechainFork(11)
    assertNotNull("Expected to get sidechain fork", sidechainConsensusFork4)
    assertTrue("Expected not to get sidechain fork", sidechainConsensusFork4.isInstanceOf[SidechainFork1])
  }
}

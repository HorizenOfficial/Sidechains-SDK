package io.horizen.fork

import org.junit.Assert.{assertEquals, assertNotEquals}
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
    assertEquals("Expected failure on ForkManager initialization", true, res.isFailure)

    res = Try.apply(ForkManager.init(new BadForkConfigurator, "regtest"))
    assertEquals("Expected failure on ForkManager initialization", true, res.isFailure)

    res = Try.apply(ForkManager.init(simpleForkConfigurator, "regtest"))
    assertEquals("Expected successed ForkManager initialization", true, res.isSuccess)

    res = Try.apply(ForkManager.init(simpleForkConfigurator, "regtest"))
    assertEquals("Expected failure on ForkManager initialization", true, res.isFailure)

    val mainchainFork1 = ForkManager.getMainchainFork(419)
    assertEquals("Expected not to get mainchain fork", null, mainchainFork1)

    val mainchainFork2 = ForkManager.getMainchainFork(420)
    assertNotEquals("Expected to get mainchain fork", null, mainchainFork2)

    val sidechainConsensusFork1 = ForkManager.getSidechainFork(-5)
    assertEquals("Expected not to get sidechain fork", null, sidechainConsensusFork1)

    val sidechainConsensusFork2 = ForkManager.getSidechainFork(9)
    assertNotEquals("Expected to get sidechain fork", null, sidechainConsensusFork2)
    assertEquals("Expected not to get sidechain fork", true, sidechainConsensusFork2.isInstanceOf[MandatorySidechainFork])

    val sidechainConsensusFork3 = ForkManager.getSidechainFork(10)
    assertNotEquals("Expected to get sidechain fork", null, sidechainConsensusFork3)
    assertEquals("Expected not to get sidechain fork", true, sidechainConsensusFork3.isInstanceOf[SidechainFork1])

    val sidechainConsensusFork4 = ForkManager.getSidechainFork(11)
    assertNotEquals("Expected to get sidechain fork", null, sidechainConsensusFork4)
    assertEquals("Expected not to get sidechain fork", true, sidechainConsensusFork4.isInstanceOf[SidechainFork1])
  }
}

package io.horizen.fork

import org.junit.Assert.{assertEquals, assertNotNull, assertNull, assertTrue}
import org.junit.{Before, Test}
import org.scalatestplus.junit.JUnitSuite

class ForkManagerTest extends JUnitSuite {

  @Before
  def init(): Unit = {
    ForkManager.reset()
  }

  @Test
  def ForkManagerInitialization(): Unit = {
    val forkConfigurator = new SimpleForkConfigurator

    assertThrows[IllegalArgumentException](
      "Expected failure with an invalid network name",
      ForkManager.init(forkConfigurator, "wrongname")
    )

    assertThrows[RuntimeException](
      "Expected failure with a bad fork configuration",
      ForkManager.init(new BadForkConfigurator, "regtest")
    )

    // this should not throw
    ForkManager.init(forkConfigurator, "regtest")

    assertThrows[RuntimeException](
      "Expected failure with an already initialized ForkManager",
      ForkManager.init(forkConfigurator, "regtest")
    )
  }

  @Test
  def ForkManagerMandatoryForks(): Unit = {
    val forkConfigurator = new SimpleForkConfigurator
    ForkManager.init(forkConfigurator, "regtest")

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

  @Test
  def ForkManagerOptionalForksTest(): Unit = {
    ForkManager.init(new GoodOptionalForkConfigurator, "regtest");
    assertEquals(
      "expected bar=0 at consensus epoch 0",
      Some(MustNotDecreaseFork(0, 0)),
      ForkManager.getOptionalSidechainFork[MustNotDecreaseFork](0)
    )
    assertEquals(
      "expected bar=1 at consensus epoch 1",
      Some(MustNotDecreaseFork(0, 1)),
      ForkManager.getOptionalSidechainFork[MustNotDecreaseFork](1)
    )
    assertEquals(
      "expected bar=2 at consensus epoch 2",
      Some(MustNotDecreaseFork(0, 2)),
      ForkManager.getOptionalSidechainFork[MustNotDecreaseFork](2)
    )
  }
}

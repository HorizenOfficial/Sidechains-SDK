package io.horizen.fork

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.util.Success

class BadForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, -5)
}

class ForkConfiguratorTest extends JUnitSuite {
  val badForkConfigurator = new BadForkConfigurator()
  val simpleForkConfigurator = new SimpleForkConfigurator()

  @Test
  def testConfiguration(): Unit = {
    assertEquals("Expected failed check", false, badForkConfigurator.check().isSuccess)
    assertEquals("Expected successful check", Success(()), simpleForkConfigurator.check())
  }
}

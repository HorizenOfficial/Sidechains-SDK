package io.horizen.fork

import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.util.Success

class BadForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, -5)
}

class BadSc2scForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: Map[SidechainForkConsensusEpoch, OptionalSidechainFork] = Map(
    SidechainForkConsensusEpoch(0, 0, 0) -> DefaultSc2scFork(),
    SidechainForkConsensusEpoch(0, 0, 1) -> DefaultSc2scFork(),
    SidechainForkConsensusEpoch(0, 0, 2) -> DefaultSc2scFork(),
  )
}

case class CustomTestFork(foo: Long, bar: Long) extends OptionalSidechainFork {

  private def mustNotDecrease(a: Long, b: Long): Long = {
    if (a < b) throw new RuntimeException("parameter must not decrease")
    b
  }

  override def validate(forks: Seq[OptionalSidechainFork]): Unit = {
    val customForks = forks.collect { case fork: CustomTestFork => fork }
    customForks.map(_.foo).reduceLeft(mustNotDecrease)
    customForks.map(_.bar).reduceLeft(mustNotDecrease)
  }
}

class BadOptionalForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: Map[SidechainForkConsensusEpoch, OptionalSidechainFork] = Map(
    SidechainForkConsensusEpoch(0, 0, 0) -> CustomTestFork(0, 0),
    SidechainForkConsensusEpoch(0, 0, 1) -> CustomTestFork(0, 1),
    SidechainForkConsensusEpoch(0, 0, 2) -> CustomTestFork(0, 1),
  )
}

class ForkConfiguratorTest extends JUnitSuite {
  val badForkConfigurator = new BadForkConfigurator()
  val badSc2scForkConfigurator = new BadSc2scForkConfigurator()
  val badOptionalForkConfigurator = new BadOptionalForkConfigurator()
  val simpleForkConfigurator = new SimpleForkConfigurator()
  val optionalForkConfigurator = new OptionalForkConfigurator()

  @Test
  def testConfiguration(): Unit = {
    assertTrue("Expected failed check", badForkConfigurator.check().isFailure)
    assertTrue("Expected failed check", badSc2scForkConfigurator.check().isFailure)
    assertTrue("Expected failed check", badOptionalForkConfigurator.check().isFailure)
    assertEquals("Expected successful check", Success(()), simpleForkConfigurator.check())
  }
}

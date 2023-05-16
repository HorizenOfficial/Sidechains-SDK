package io.horizen.fork

import io.horizen.utils.Pair
import org.junit.Assert.{assertEquals, assertNotEquals}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Success

class BadForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, -5)
}

class BadSc2scForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {
    Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
      new Pair(SidechainForkConsensusEpoch(0, 0, 0), DefaultSc2scFork()),
      new Pair(SidechainForkConsensusEpoch(0, 0, 1), DefaultSc2scFork()),
      new Pair(SidechainForkConsensusEpoch(0, 0, 2), DefaultSc2scFork()),
    ).asJava
  }
}

case class MustNotDecreaseFork(foo: Long, bar: Long) extends OptionalSidechainFork {
  private def mustNotDecrease(a: Long, b: Long): Long = {
    if (b < a) throw new RuntimeException("parameter must not decrease")
    b
  }

  override def validate(forks: Seq[OptionalSidechainFork]): Unit = {
    val customForks = forks.collect { case fork: MustNotDecreaseFork => fork }
    customForks.map(_.foo).reduceLeft(mustNotDecrease)
    customForks.map(_.bar).reduceLeft(mustNotDecrease)
  }
}

class BadOptionalForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
    Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
      new Pair(SidechainForkConsensusEpoch(0, 0, 0), MustNotDecreaseFork(10, 0)),
      new Pair(SidechainForkConsensusEpoch(0, 0, 1), MustNotDecreaseFork(20, 1)),
      // this should fail validation because the second value decreased
      new Pair(SidechainForkConsensusEpoch(0, 0, 2), MustNotDecreaseFork(30, 0)),
    ).asJava
}

class GoodOptionalForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
    Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
      new Pair(SidechainForkConsensusEpoch(0, 0, 0), MustNotDecreaseFork(0, 0)),
      new Pair(SidechainForkConsensusEpoch(0, 0, 1), MustNotDecreaseFork(0, 1)),
      new Pair(SidechainForkConsensusEpoch(0, 0, 2), MustNotDecreaseFork(0, 2)),
    ).asJava
}

class ForkConfiguratorTest extends JUnitSuite {
  val badForkConfigurator = new BadForkConfigurator()
  val badSc2scForkConfigurator = new BadSc2scForkConfigurator()
  val badOptionalForkConfigurator = new BadOptionalForkConfigurator()
  val goodOptionalForkConfigurator = new GoodOptionalForkConfigurator()
  val simpleForkConfigurator = new SimpleForkConfigurator()

  @Test
  def testConfiguration(): Unit = {
    assertNotEquals("Expected failed check", Success(()), badForkConfigurator.check())
    assertNotEquals("Expected failed check", Success(()), badSc2scForkConfigurator.check())
    assertNotEquals("Expected failed check", Success(()), badOptionalForkConfigurator.check())
    assertEquals("Expected failed check", Success(()), goodOptionalForkConfigurator.check())
    assertEquals("Expected successful check", Success(()), simpleForkConfigurator.check())
  }
}

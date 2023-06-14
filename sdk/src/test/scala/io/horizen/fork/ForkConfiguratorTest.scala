package io.horizen.fork

import io.horizen.account.fork.GasFeeFork
import io.horizen.utils.Pair
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter

/**
 * "Bad" fork because one of the activation epochs is negative, i.e. before the default that activate at 0.
 */
class BadForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, -5)
}

/**
 * Too many sc2sc forks, only one is allowed.
 */
class BadSc2scForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {
    Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
      new Pair(SidechainForkConsensusEpoch(0, 0, 0), Sc2ScFork(sc2ScCanSend = true)),
      new Pair(SidechainForkConsensusEpoch(0, 0, 1), Sc2ScFork(sc2ScCanReceive = true)),
    ).asJava
  }
}

/**
 * Defines a custom fork types that validates that its values are never decreasing in a fork.
 */
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

/**
 * Uses the MustNotDecreaseFork and fails its validation (one of the values decreases in a fork).
 */
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
      // this fork should be ignored during validation of MustNotDecreaseFork, as it is of a different type
      new Pair(SidechainForkConsensusEpoch(1, 1, 1), GasFeeFork()),
      new Pair(SidechainForkConsensusEpoch(1, 1, 1), MustNotDecreaseFork(0, 1)),
      new Pair(SidechainForkConsensusEpoch(2, 2, 2), MustNotDecreaseFork(0, 2)),
    ).asJava
}

class Sc2ScOptionalForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 5)

  override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
    Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
      new Pair(SidechainForkConsensusEpoch(0, 0, 0), Sc2ScFork(sc2ScCanSend = true, sc2ScCanReceive = true))
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
    assertThrows[RuntimeException](badForkConfigurator.check())
    assertThrows[RuntimeException](badSc2scForkConfigurator.check())
    assertThrows[RuntimeException](badOptionalForkConfigurator.check())
    // these should not throw
    goodOptionalForkConfigurator.check()
    simpleForkConfigurator.check()
  }
}

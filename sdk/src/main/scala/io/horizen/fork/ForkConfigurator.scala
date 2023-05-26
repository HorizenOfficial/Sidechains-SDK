package io.horizen.fork

import io.horizen.utils.Pair

import java.util
import scala.jdk.CollectionConverters.asScalaBufferConverter

abstract class ForkConfigurator {

  /**
   * Mandatory for every sidechain to provide an epoch number here.
   */
  val fork1activation: SidechainForkConsensusEpoch

  /**
   * Return the map of configured consensus epoch numbers to mandatory sidechain forks.
   */
  final lazy val mandatorySidechainForks: Map[SidechainForkConsensusEpoch, MandatorySidechainFork] =
    MandatorySidechainFork.forks(fork1activation)

  /**
   * Return the map of optional sidechain forks and their consensus epoch numbers.
   */
  final lazy val optionalSidechainForks: Map[SidechainForkConsensusEpoch, OptionalSidechainFork] =
    OptionalSidechainFork.forks(getOptionalSidechainForks.asScala.map(x => (x.getKey, x.getValue)).toMap)

  /**
   * Return a list of optional forks with their consensus epoch numbers.
   */
  def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
    new util.ArrayList()

  final def check(): Unit = {
    // fork configurations are validated on first access
    mandatorySidechainForks
    optionalSidechainForks
  }
}

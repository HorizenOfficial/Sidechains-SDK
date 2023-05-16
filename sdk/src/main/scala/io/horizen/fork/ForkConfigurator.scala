package io.horizen.fork

import scala.util.Try

abstract class ForkConfigurator {

  /**
   * Mandatory for every sidechain to provide an epoch number here.
   */
  val fork1activation: SidechainForkConsensusEpoch

  /**
   * Return the map of configured activations to mandatory sidechain forks.
   */
  final lazy val mandatorySidechainForks: Map[SidechainForkConsensusEpoch, MandatorySidechainFork] =
    MandatorySidechainFork.forks(fork1activation)

  /**
   * Return the map of optional sidechain forks and their activations.
   */
  final lazy val optionalSidechainForks: Map[SidechainForkConsensusEpoch, OptionalSidechainFork] =
    OptionalSidechainFork.forks(getOptionalSidechainForks)

  /**
   * TODO: refactor somehow, because the scala Map is ugly to work with in Java
   */
  def getOptionalSidechainForks: Map[SidechainForkConsensusEpoch, OptionalSidechainFork] = Map()

  final def check(): Try[Unit] = Try {
    // fork configurations are validated on first access
    mandatorySidechainForks
    optionalSidechainForks
  }
}

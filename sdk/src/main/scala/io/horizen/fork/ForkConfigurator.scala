package io.horizen.fork

import scala.util.Try

abstract class ForkConfigurator {

  /**
   * Mandatory for every sidechain to provide an epoch number here.
   */
  val fork1activation: SidechainForkConsensusEpoch

  def getMandatorySidechainForks: Map[SidechainForkConsensusEpoch, MandatorySidechainFork] =
    MandatorySidechainFork.forks(fork1activation)

  final def check(): Try[Unit] = Try {
    // fork configuration is validated on first access
    getMandatorySidechainForks
  }
}

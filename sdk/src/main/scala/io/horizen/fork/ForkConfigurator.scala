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
  final lazy val optionalSidechainForks: Seq[(SidechainForkConsensusEpoch, OptionalSidechainFork)] =
    getOptionalSidechainForks.asScala.map(x => (x.getKey, x.getValue))

  /**
   * Return a list of optional forks with their consensus epoch numbers.
   */
  def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
    new util.ArrayList()

  final def check(): Unit = {
    // validate activations
    ForkUtil.validate(mandatorySidechainForks)
    ForkUtil.validate(optionalSidechainForks)
    // allow each optional fork instance to validate against all other optional forks
    val values = optionalSidechainForks.map(_._2)
    optionalSidechainForks.foreach({ case (_, fork) => fork.validate(values) })
  }
}

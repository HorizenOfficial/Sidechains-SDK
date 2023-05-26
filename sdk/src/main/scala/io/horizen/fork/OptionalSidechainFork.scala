package io.horizen.fork

trait OptionalSidechainFork {
  def validate(forks: Seq[OptionalSidechainFork]): Unit = ()
}

object OptionalSidechainFork {
  def forks(forks: Map[SidechainForkConsensusEpoch, OptionalSidechainFork])
      : Map[SidechainForkConsensusEpoch, OptionalSidechainFork] = {
    val values = forks.values.toSeq
    // allow each fork instance to validate against all other forks
    forks.foreach({ case (_, fork) => fork.validate(values) })
    // validate activations
    ForkUtil.validate(forks)
  }
}

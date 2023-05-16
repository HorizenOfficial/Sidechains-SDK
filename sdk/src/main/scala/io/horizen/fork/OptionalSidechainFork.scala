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

trait Sc2scFork extends OptionalSidechainFork {
  val sc2scCanSend: Boolean
  val sc2scCanReceive: Boolean

  throw new RuntimeException("Sc2scFork is not supported yet")

  final override def validate(forks: Seq[OptionalSidechainFork]): Unit = {
    val sc2sc2Forks = forks.collect { case fork: Sc2scFork => fork }
    if (sc2sc2Forks.length > 2) throw new RuntimeException("only 1 additional sc2sc fork is allowed")
  }
}

case class DefaultSc2scFork(
    sc2scCanSend: Boolean = false,
    sc2scCanReceive: Boolean = false,
) extends Sc2scFork

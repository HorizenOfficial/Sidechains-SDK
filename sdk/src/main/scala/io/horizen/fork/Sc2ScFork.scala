package io.horizen.fork

case class Sc2ScFork(
                      sc2ScCanSend: Boolean = false,
                      sc2ScCanReceive: Boolean = false,
) extends OptionalSidechainFork {

  final override def validate(forks: Seq[OptionalSidechainFork]): Unit = {
    val sc2sc2Forks = forks.collect { case fork: Sc2ScFork => fork }
    if (sc2sc2Forks.length > 1) throw new RuntimeException("only 1 sc2sc fork is allowed")
  }
}

object Sc2ScFork {
  def get(epochNumber: Int): Sc2ScFork = {
    ForkManager.getOptionalSidechainFork[Sc2ScFork](epochNumber).getOrElse(DefaultSc2scFork)
  }

  val DefaultSc2scFork: Sc2ScFork = Sc2ScFork()
}

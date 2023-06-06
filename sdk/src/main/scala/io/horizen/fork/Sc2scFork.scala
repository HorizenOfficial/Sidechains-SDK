package io.horizen.fork

case class Sc2scFork(
    sc2scCanSend: Boolean = false,
    sc2scCanReceive: Boolean = false,
) extends OptionalSidechainFork {

  // Sc2sc forks are not supported yet, any use will throw an exception for now
  throw new RuntimeException("Sc2scFork is not supported yet")

  final override def validate(forks: Seq[OptionalSidechainFork]): Unit = {
    val sc2sc2Forks = forks.collect { case fork: Sc2scFork => fork }
    if (sc2sc2Forks.length > 1) throw new RuntimeException("only 1 sc2sc fork is allowed")
  }
}

object Sc2scFork {
  def get(epochNumber: Int): Sc2scFork = {
    ForkManager.getOptionalSidechainFork[Sc2scFork](epochNumber).getOrElse(DefaultSc2scFork)
  }

  val DefaultSc2scFork: Sc2scFork = Sc2scFork()
}

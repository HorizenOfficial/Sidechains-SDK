package io.horizen.fork

case class ActiveSlotCoefficientFork(
                                    // It defines the desired % of filled slots. The default value is -1 meaning we are not using this value inside the vrf lottery
                                    activeSlotCoefficient: Double = -1
                                    ) extends OptionalSidechainFork {

  if (isArgumentOutOfActiveSlotCoefficientForkDomain)
    throw new IllegalArgumentException("The active slot coefficient, if defined, must be > 0 and <= 1")

  private def isArgumentOutOfActiveSlotCoefficientForkDomain = {
    activeSlotCoefficient != -1 && !ActiveSlotCoefficientFork.isArgumentInActiveSlotCoefficientForkDomain(activeSlotCoefficient)
  }
}

object ActiveSlotCoefficientFork {
  def get(epochNumber: Int): ActiveSlotCoefficientFork = {
    ForkManager.getOptionalSidechainFork[ActiveSlotCoefficientFork](epochNumber).getOrElse(DefaultActiveSlotCoefficientFork)
  }

  val DefaultActiveSlotCoefficientFork: ActiveSlotCoefficientFork = ActiveSlotCoefficientFork()
  def isArgumentInActiveSlotCoefficientForkDomain(arg: Double): Boolean = arg > 0 && arg <= 1
}
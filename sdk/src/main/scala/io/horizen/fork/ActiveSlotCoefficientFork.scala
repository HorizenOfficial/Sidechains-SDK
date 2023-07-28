package io.horizen.fork

case class ActiveSlotCoefficientFork(
                                    activeSlotCoefficient: Double = -1
                                    ) extends OptionalSidechainFork

object ActiveSlotCoefficientFork {
  def get(epochNumber: Int): ActiveSlotCoefficientFork = {
    ForkManager.getOptionalSidechainFork[ActiveSlotCoefficientFork](epochNumber).getOrElse(DefaultActiveSlotCoefficientFork)
  }

  val DefaultActiveSlotCoefficientFork: ActiveSlotCoefficientFork = ActiveSlotCoefficientFork()
}
package io.horizen.fork

object ForkUtil {

  def selectNetwork[T <: ForkActivation, F](network: String, forks: Traversable[(T, F)]): Traversable[(Int, F)] = {
    forks.map { case (key, value) => key.get(network) -> value }
  }

  /**
   * Find the first invalid fork activation, i.e. with an activation less than a previous fork.
   */
  private def validateForkActivation[T <: ForkActivation, F](network: String, forks: Traversable[(T, F)]): Option[F] = {
    selectNetwork(network, forks).foldLeft(0) { case (last, (activation, fork)) =>
      // if the activation is less than the last, the fork configuration is invalid
      if (activation < last) throw new RuntimeException(
        s"invalid fork configuration on $network: fork $fork is activated at $activation but the previous fork already activated at $last"
      )
      // compare the next fork to this activation
      activation
    }
    // no invalid fork found
    None
  }

  /**
   * Validate the order of activaions for the sequence of forks for all networks (configuration sanity check)
   */
  def validate[T <: ForkActivation, F](forks: Traversable[(T, F)]): Unit = {
    validateForkActivation("regtest", forks)
    validateForkActivation("testnet", forks)
    validateForkActivation("mainnet", forks)
  }

}

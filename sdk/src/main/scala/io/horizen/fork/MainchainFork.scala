package io.horizen.fork

/**
 * Defines the mainchain block height per network at which a fork becomes active.
 */
case class MainchainForkHeight(regtest: Int, testnet: Int, mainnet: Int) extends ForkActivation

/**
 * Mainchain fork variables. Defines variables that can be changed at forks in the mainchain.
 */
sealed trait MainchainFork {
  val getSidechainTxVersion: Int
  val getCertificateVersion: Int
  val getNewBlockVersion: Int
}

/**
 * Introduced when sidechain support was added to the mainchain.
 */
case class SidechainSupportMainchainFork(
    getSidechainTxVersion: Int = 0xfffffffb,
    getCertificateVersion: Int = 0xfffffffc,
    getNewBlockVersion: Int = 0x3,
) extends MainchainFork

object MainchainFork {

  /**
   * Defines all mainchain forks, hardcoded.
   */
  val forks: Map[MainchainForkHeight, MainchainFork] = Map(
    MainchainForkHeight(420, 926225, 1047624) -> SidechainSupportMainchainFork()
  )

}

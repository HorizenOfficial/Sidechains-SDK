package io.horizen.fork

/**
 * Mainchain fork variables. Defines variables that can be changed at forks in the mainchain.
 */
case class MainchainFork(
    getSidechainTxVersion: Int,
    getCertificateVersion: Int,
    getNewBlockVersion: Int,
)

/**
 * Defines the mainchain block height per network at which a fork becomes active.
 */
case class MainchainForkHeight(regtest: Int, testnet: Int, mainnet: Int) extends ForkActivation

object MainchainFork {

  /**
   * List of mainchain forks, hardcoded.
   */
  val forks: Map[MainchainForkHeight, MainchainFork] = ForkUtil.validate(
    Map(
      MainchainForkHeight(420, 926225, 1047624) -> MainchainFork(
        getSidechainTxVersion = 0xfffffffb,
        getCertificateVersion = 0xfffffffc,
        getNewBlockVersion = 0x3,
      )
    )
  )

}

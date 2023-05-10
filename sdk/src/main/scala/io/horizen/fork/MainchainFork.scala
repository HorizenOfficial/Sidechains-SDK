package io.horizen.fork

/**
 * Mainchain fork variables. Defines variables that can be changed at specific forks in the mainchain.
 */
class MainchainFork(val heights: MainchainForkHeight) {
  val getSidechainTxVersion: Int = MainchainFork.CERT_VERSION
  val getCertificateVersion: Int = MainchainFork.TRANSACTION_VERSION
  val getNewBlockVersion: Int = MainchainFork.BLOCK_VERSION
}

/**
 * Defines the block height per network at which a fork becomes active.
 */
case class MainchainForkHeight(regtest: Int, testnet: Int, mainnet: Int)

object MainchainFork {
  private val CERT_VERSION: Int = 0xfffffffb
  private val TRANSACTION_VERSION: Int = 0xfffffffc
  private val BLOCK_VERSION: Int = 0x3
  val DEFAULT_MAINCHAIN_FORK_HEIGHTS: MainchainForkHeight = MainchainForkHeight(420, 926225, 1047624)
}

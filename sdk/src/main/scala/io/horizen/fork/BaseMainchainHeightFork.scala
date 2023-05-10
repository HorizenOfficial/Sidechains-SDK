package io.horizen.fork

class BaseMainchainHeightFork(val heights: MainchainForkHeight) {
  val getSidechainTxVersion: Int = BaseMainchainHeightFork.CERT_VERSION
  val getCertificateVersion: Int = BaseMainchainHeightFork.TRANSACTION_VERSION
  val getNewBlockVersion: Int = BaseMainchainHeightFork.BLOCK_VERSION
}

case class MainchainForkHeight(regtestHeight: Int, testnetHeight: Int, mainnetHeight: Int) {}

object BaseMainchainHeightFork {
  private val CERT_VERSION: Int = 0xfffffffb
  private val TRANSACTION_VERSION: Int = 0xfffffffc
  private val BLOCK_VERSION: Int = 0x3
  val DEFAULT_MAINCHAIN_FORK_HEIGHTS: MainchainForkHeight = MainchainForkHeight(420, 926225, 1047624)
}

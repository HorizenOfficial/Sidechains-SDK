package io.horizen.fork

class BaseMainchainHeightFork(val heights: MainchainForkHeight) {
  def getSidechainTxVersion(): Int = {BaseMainchainHeightFork.CERT_VERSION}
  def getCertificateVersion(): Int = {BaseMainchainHeightFork.TRANSACTION_VERSION}
  def getNewBlockVersion(): Int = {BaseMainchainHeightFork.BLOCK_VERSION}
}

case class MainchainForkHeight(regtestHeight: Int, testnetHeight: Int, mainnetHeight: Int) {}

object BaseMainchainHeightFork {
  val CERT_VERSION : Int = 0xFFFFFFFB
  val TRANSACTION_VERSION : Int = 0xFFFFFFFC
  val BLOCK_VERSION: Int = 0x3
  val DEFAULT_MAINCHAIN_FORK_HEIGHTS = MainchainForkHeight(420, 926225, 1047624)
}

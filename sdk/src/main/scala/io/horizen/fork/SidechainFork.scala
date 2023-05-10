package io.horizen.fork

/**
 * Sidechain Fork variables. Defines variables that can be modified at specific forks.
 */
class SidechainFork(val epochNumber: SidechainForkConsensusEpoch) {
  val backwardTransferLimitEnabled: Boolean = false
  val openStakeTransactionEnabled: Boolean = false
  val nonceLength: Int = 8
  val stakePercentageForkApplied: Boolean = false
  val ftMinAmount: Long = 0
  val coinBoxMinAmount: Long = 0
}

/**
 * Defines the consensus epoch number per network at which a fork becomes active.
 */
case class SidechainForkConsensusEpoch(regtest: Int, testnet: Int, mainnet: Int)

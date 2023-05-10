package io.horizen.fork

/**
 * Sidechain Fork variables. Defines variables that can be modified at specific forks.
 */
class BaseConsensusEpochFork(val epochNumber: ForkConsensusEpochNumber) {
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
case class ForkConsensusEpochNumber(regtestEpochNumber: Int, testnetEpochNumber: Int, mainnetEpochNumber: Int)

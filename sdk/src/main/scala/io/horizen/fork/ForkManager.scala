package io.horizen.fork

object ForkManager {
  private var networkName: String = _

  /**
   * List of mandatory mainchain forks, hardcoded.
   */
  private val mainchainForks: Seq[MainchainFork] = Seq(
    new MainchainFork(MainchainFork.DEFAULT_MAINCHAIN_FORK_HEIGHTS)
  )

  /**
   * List of mandatory sidechain forks, the activation points have to be configured by the sidechain.
   */
  private var consensusEpochForks: Seq[SidechainFork] = Seq()

  /**
   * Finds the latest fork in the given sequence of forks with an activation height less or equal than the given height.
   */
  private def findActiveFork[T](forks: Seq[T], height: Int)(fun: T => Int): Option[T] = {
    forks.foldLeft(Option.empty[T]) { (active, fork) =>
      if (fun(fork) <= height) Some(fork)
      else return active
    }
  }

  def getMainchainFork(mainchainHeight: Int): MainchainFork = {
    if (networkName == null) throw new RuntimeException("Forkmanager hasn't been initialized.")
    if (mainchainForks.isEmpty) throw new RuntimeException("MainchainForks list is empty")
    findActiveFork(mainchainForks, mainchainHeight) { fork =>
      networkName match {
        case "regtest" => fork.heights.regtest
        case "testnet" => fork.heights.testnet
        case "mainnet" => fork.heights.mainnet
      }
    }.orNull
  }

  def getSidechainFork(consensusEpoch: Int): SidechainFork = {
    if (networkName == null) throw new RuntimeException("Forkmanager hasn't been initialized.")
    if (consensusEpochForks.isEmpty) throw new RuntimeException("ConsensusEpochForks list is empty")
    findActiveFork(consensusEpochForks, consensusEpoch) { fork =>
      networkName match {
        case "regtest" => fork.epochNumber.regtest
        case "testnet" => fork.epochNumber.testnet
        case "mainnet" => fork.epochNumber.mainnet
      }
    }.orNull
  }

  def init(forkConfigurator: ForkConfigurator, networkName: String): Unit = {
    if (this.networkName != null) throw new IllegalStateException("ForkManager is already initialized.")

    val saneNetworkName = networkName match {
      case "regtest" | "testnet" | "mainnet" => networkName
      case _ => throw new IllegalArgumentException("Unknown network type.")
    }

    this.consensusEpochForks = forkConfigurator.check().get
    this.networkName = saneNetworkName
  }

  private[horizen] def reset(): Unit = {
    this.networkName = null
    this.consensusEpochForks = Seq()
  }
}

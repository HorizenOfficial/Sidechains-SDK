package io.horizen.fork

object ForkManager {
  private var initialized = false

  /**
   * List of mainchain forks, hardcoded.
   */
  private var mainchainForks: Map[Int, MainchainFork] = _

  /**
   * List of mandatory sidechain forks, the activation points have to be configured by the sidechain.
   */
  private var sidechainForks: Map[Int, MandatorySidechainFork] = _

  /**
   * List of optional sidechain forks, these are configured by the sidechain.
   */
  private var optionalSidechainForks: Seq[(Int, OptionalSidechainFork)] = _

  /**
   * Finds the latest fork in the given sequence of forks with an activation height less or equal than the given height.
   */
  private def findActiveFork[T](forks: Traversable[(Int, T)], height: Int): Option[T] = {
    forks.foldLeft(Option.empty[T]) { case (active, (activation, fork)) =>
      if (activation <= height) Some(fork)
      else return active
    }
  }

  private def assertInitialized(): Unit = {
    if (!initialized) throw new RuntimeException("Forkmanager hasn't been initialized.")
  }

  def getMainchainFork(mainchainHeight: Int): MainchainFork = {
    assertInitialized()
    findActiveFork(mainchainForks, mainchainHeight).orNull
  }

  def getSidechainFork(consensusEpoch: Int): MandatorySidechainFork = {
    assertInitialized()
    findActiveFork(sidechainForks, consensusEpoch).orNull
  }

  def getOptionalSidechainFork[T <: OptionalSidechainFork : Manifest](consensusEpoch: Int): Option[T] = {
    assertInitialized()
    val forksOfTypeT = optionalSidechainForks.collect({ case (i, fork: T) => (i, fork) })
    findActiveFork(forksOfTypeT, consensusEpoch)
  }

  def hasOptionalForkOfType[ForkType](): Boolean = {
    optionalSidechainForks.exists {optFork => optFork._2.isInstanceOf[ForkType]}
  }

  def init(forkConfigurator: ForkConfigurator, networkName: String): Unit = {
    if (initialized) throw new IllegalStateException("ForkManager is already initialized.")

    ForkUtil.validate(MainchainFork.forks)
    forkConfigurator.check()

    // preselect the network as it cannot change during runtime
    mainchainForks = ForkUtil.selectNetwork(networkName, MainchainFork.forks).toMap
    sidechainForks = ForkUtil.selectNetwork(networkName, forkConfigurator.mandatorySidechainForks).toMap
    optionalSidechainForks = ForkUtil.selectNetwork(networkName, forkConfigurator.optionalSidechainForks).toSeq

    initialized = true
  }

  def reset(): Unit = {
    initialized = false
  }
}

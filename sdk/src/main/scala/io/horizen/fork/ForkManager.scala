package io.horizen.fork

import scala.reflect.ClassTag

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
  private var optionalSidechainForks: Map[Int, OptionalSidechainFork] = _

  /**
   * Finds the latest fork in the given sequence of forks with an activation height less or equal than the given height.
   */
  private def findActiveFork[T](forks: Map[Int, T], height: Int): Option[T] = {
    // TODO: improve with binary search and/or TreeMap instead of Map
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

  def getOptionalSidechainFork[T <: OptionalSidechainFork](consensusEpoch: Int)(implicit tag: ClassTag[T]): Option[T] = {
    assertInitialized()
    val forksOfTypeT = optionalSidechainForks.collect({ case (i, fork: T) => (i, fork) })
    findActiveFork(forksOfTypeT, consensusEpoch)
  }

  def init(forkConfigurator: ForkConfigurator, networkName: String): Unit = {
    if (initialized) throw new IllegalStateException("ForkManager is already initialized.")

    // preselect the network as it cannot change during runtime
    mainchainForks = ForkUtil.selectNetwork(networkName, MainchainFork.forks)
    sidechainForks = ForkUtil.selectNetwork(networkName, forkConfigurator.mandatorySidechainForks)
    optionalSidechainForks = ForkUtil.selectNetwork(networkName, forkConfigurator.optionalSidechainForks)

    initialized = true
  }

  private[horizen] def reset(): Unit = {
    initialized = false
  }
}

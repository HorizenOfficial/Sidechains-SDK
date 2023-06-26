package io.horizen.fork

object ForkManagerUtil {
  def initializeForkManager(forkConfigurator: ForkConfigurator, networkName: String): Unit = {
    ForkManager.reset()
    ForkManager.init(forkConfigurator, networkName)
  }
}

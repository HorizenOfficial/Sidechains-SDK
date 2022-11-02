package com.horizen.fork
import scala.util.Try

class ForkManagerUtil {
  def initializeForkManager(forkConfigurator:ForkConfigurator, networkName:String): Try[Unit] = Try {
    ForkManager.reset()
    ForkManager.init(forkConfigurator, networkName)
  }
}

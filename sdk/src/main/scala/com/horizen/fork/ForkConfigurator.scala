package com.horizen.fork

import scala.util.{Failure, Success, Try}

abstract class ForkConfigurator {
  def getBaseSidechainConsensusEpochNumbers():scConsensusEpochNumber

  final def check(): Try[Unit] = {
    val baseConsensusEpochNumbers = getBaseSidechainConsensusEpochNumbers()
    if ((baseConsensusEpochNumbers.mainnetEpochNumber < 0) ||
      (baseConsensusEpochNumbers.testnetEpochNumber < 0) ||
      (baseConsensusEpochNumbers.regtestEpochNumber < 0))
      Failure(new RuntimeException("Inappropriate baseConsensusEpoch activation height."))
    else
      Success()

    /*
     * Put checks for other implemented forks here
     */
  }
}

package io.horizen.fork

import scala.util.{Failure, Success, Try}

abstract class ForkConfigurator {
  final def getBaseSidechainConsensusEpochNumbers():ForkConsensusEpochNumber = {
    ForkConsensusEpochNumber(0, 0, 0)
  }

  def getSidechainFork1():ForkConsensusEpochNumber

  final def check(): Try[Unit] = {
    val baseConsensusEpochNumbers = getBaseSidechainConsensusEpochNumbers()
    val fork1EpochNumbers = getSidechainFork1()

    //Fork 1
    if ((fork1EpochNumbers.regtestEpochNumber < baseConsensusEpochNumbers.regtestEpochNumber)
      || (fork1EpochNumbers.testnetEpochNumber < baseConsensusEpochNumbers.testnetEpochNumber)
      || (fork1EpochNumbers.mainnetEpochNumber < baseConsensusEpochNumbers.mainnetEpochNumber))
      Failure(new RuntimeException("Inappropriate SidechainFork1 activation height."))
    /*
    * Put checks for each other implemented forks here, comparing corresponding epoch numbers with previous fork
    */
    else
      Success()
  }
}

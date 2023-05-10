package io.horizen.fork

import scala.util.{Failure, Success, Try}

abstract class ForkConfigurator {

  /**
   * The default values are always active since genesis.
   */
  private val getBaseSidechainConsensusEpochNumbers = ForkConsensusEpochNumber(0, 0, 0)

  /**
   * Mandatory for every sidechain to provide an epoch number here.
   */
  val getSidechainFork1: ForkConsensusEpochNumber

  /**
   * List of sidechain consensus epoch forks
   */
  private lazy val forks = Seq(
    new BaseConsensusEpochFork(getBaseSidechainConsensusEpochNumbers),
    new SidechainFork1(getSidechainFork1),
  )

  /**
   * Finds and returns the first fork in a sequence of forks where the configured activation height is invalid.
   */
  private def findBadFork[T](elements: Seq[T])(fun: T => Int): Option[T] = {
    elements.foldLeft(0) { (lastHeight, fork) =>
      fun(fork) match {
        // if the activation height is less than the last we found an invalid fork configuration
        case height if height < lastHeight => return Some(fork)
        case height => height
      }
    }
    None
  }

  /**
   * Validate monotonic epoch numbers for the sequence of forks for all networks (configuration sanity check)
   * @return
   *   sequence of forks
   */
  final def check(): Try[Seq[BaseConsensusEpochFork]] = {
    findBadFork(forks)(_.epochNumber.mainnetEpochNumber).map {
      badFork => return Failure(new RuntimeException(s"invalid activation height on mainnet for fork: $badFork"))
    }

    findBadFork(forks)(_.epochNumber.testnetEpochNumber).map {
      badFork => return Failure(new RuntimeException(s"invalid activation height on testnet for fork: $badFork"))
    }

    findBadFork(forks)(_.epochNumber.regtestEpochNumber).map {
      badFork => return Failure(new RuntimeException(s"invalid activation height on regtest for fork: $badFork"))
    }

    Success(forks)
  }
}

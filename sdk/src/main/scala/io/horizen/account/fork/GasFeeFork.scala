package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

import java.math.BigInteger

case class GasFeeFork(
    blockGasLimit: BigInteger = BigInteger.valueOf(30000000),
    baseFeeElasticityMultiplier: BigInteger = BigInteger.valueOf(2),
    baseFeeChangeDenominator: BigInteger = BigInteger.valueOf(8),
    baseFeeMinimum: BigInteger = BigInteger.ZERO,
) extends OptionalSidechainFork

object GasFeeFork {
  def get(epochNumber: Int): GasFeeFork = {
    ForkManager.getOptionalSidechainFork[GasFeeFork](epochNumber).getOrElse(DefaultGasFeeFork)
  }

  val DefaultGasFeeFork: GasFeeFork = GasFeeFork()
}

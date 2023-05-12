package io.horizen.fork

import java.math.BigInteger

class OptionalSidechainFork(val epochNumber: SidechainForkConsensusEpoch) {
  val blockGasLimit: BigInteger = BigInteger.valueOf(30000000)
  val baseFeeElasticityMultiplier: BigInteger = BigInteger.valueOf(2)
  val baseFeeChangeDenominator: BigInteger = BigInteger.valueOf(8)
  val baseFeeMinimum: BigInteger = BigInteger.ZERO
}

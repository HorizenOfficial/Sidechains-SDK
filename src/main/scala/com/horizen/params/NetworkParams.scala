package com.horizen.params

import java.math.BigInteger

trait NetworkParams {
  // Mainchain ProofOfWork parameters:

  val EquihashN: Int
  val EquihashK: Int
  val EquihashSolutionLength: Int // VarInt length + solution bytes length

  val powLimit: BigInteger
  val nPowAveragingWindow: Int
  val nPowMaxAdjustDown: Int
  val nPowMaxAdjustUp: Int
  val nPowTargetSpacing: Int
  final def averagingWindowTimespan: Int = nPowAveragingWindow * nPowTargetSpacing
  final def MinActualTimespan: Int = (averagingWindowTimespan * (100 - nPowMaxAdjustUp  )) / 100
  final def MaxActualTimespan: Int = (averagingWindowTimespan * (100 + nPowMaxAdjustDown)) / 100
}

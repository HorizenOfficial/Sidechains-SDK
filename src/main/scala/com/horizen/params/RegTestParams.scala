package com.horizen.params
import java.math.BigInteger

import scorex.util.ModifierId
import scorex.util.bytesToId

class RegTestParams extends NetworkParams {
  override val EquihashN: Int = 48
  override val EquihashK: Int = 5
  override val EquihashVarIntLength: Int = 1
  override val EquihashSolutionLength: Int = 36

  override val powLimit: BigInteger = new BigInteger("0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f", 16)
  override val nPowAveragingWindow: Int = 17
  override val nPowMaxAdjustDown: Int = 0 // Turn off adjustment down
  override val nPowMaxAdjustUp: Int = 0 // Turn off adjustment up
  override val nPowTargetSpacing: Int = 150 // 2.5 * 60

  override val sidechainId: Array[Byte] = new Array[Byte](32) // TO DO: in future RegTest will be a class, where specific sidechainId will be set
  override val sidechainGenesisBlockId: ModifierId = bytesToId(new Array[Byte](32)) // TO DO: in future RegTest will be a class, where specific genesis block id will be set
}

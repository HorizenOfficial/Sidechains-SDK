package com.horizen.fork

import com.horizen.utils.ZenCoinsUtils

class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {

  override val ftMinAmount: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)

  override val coinBoxMinAmount: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)
}

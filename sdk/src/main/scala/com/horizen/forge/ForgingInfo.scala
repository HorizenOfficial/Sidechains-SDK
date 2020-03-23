package com.horizen.forge

import com.horizen.consensus.ConsensusEpochAndSlot

case class ForgingInfo(consensusSecondsInSlot: Int, consensusSlotsInEpoch: Int,
                       currentBestEpochAndSlot: ConsensusEpochAndSlot)

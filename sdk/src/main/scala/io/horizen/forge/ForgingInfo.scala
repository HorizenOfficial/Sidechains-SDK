package io.horizen.forge

import io.horizen.consensus.ConsensusEpochAndSlot

case class ForgingInfo(consensusSecondsInSlot: Int,
                       consensusSlotsInEpoch: Int,
                       currentBestEpochAndSlot: ConsensusEpochAndSlot,
                       forgingEnabled: Boolean)

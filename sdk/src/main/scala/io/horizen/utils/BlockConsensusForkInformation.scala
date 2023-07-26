package io.horizen.utils

import io.horizen.fork.ConsensusParamsFork

case class BlockConsensusForkInformation(
                                        timestampInFork: Long,
                                        ForkStartingEpoch: Int,
                                        lastConsensusFork: (Int, ConsensusParamsFork)
                                        )

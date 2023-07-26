package io.horizen.utils

import io.horizen.fork.ConsensusParamsFork

case class BlockConsensusForkInformation(
                                          secondsInFork: Long,
                                          ForkStartingEpoch: Int,
                                          lastConsensusFork: (Int, ConsensusParamsFork)
                                        )

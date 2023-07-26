package io.horizen.utils

import io.horizen.fork.ConsensusParamsForkInfo

case class BlockConsensusForkInformation(
                                          secondsInFork: Long,
                                          ForkStartingEpoch: Int,
                                          lastConsensusFork: ConsensusParamsForkInfo
                                        )

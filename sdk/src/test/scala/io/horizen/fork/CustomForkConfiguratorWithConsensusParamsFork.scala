package io.horizen.fork

import io.horizen.account.fork.ConsensusParamsFork
import io.horizen.utils.Pair
import scala.jdk.CollectionConverters.seqAsJavaListConverter

import java.util

object CustomForkConfiguratorWithConsensusParamsFork {
  def getCustomForkConfiguratorWithConsensusParamsFork(activationHeight: Int, consensusSlotsPerEpoch: Int): ForkConfigurator = {
    class CustomForkConfigurator extends ForkConfigurator {
      /**
       * Mandatory for every sidechain to provide an epoch number here.
       */
      override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(10, 20, 0)

      override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {
        Seq(new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](SidechainForkConsensusEpoch(activationHeight, activationHeight, activationHeight), new ConsensusParamsFork(consensusSlotsPerEpoch))).asJava
      }
    }
    new CustomForkConfigurator
  }

}

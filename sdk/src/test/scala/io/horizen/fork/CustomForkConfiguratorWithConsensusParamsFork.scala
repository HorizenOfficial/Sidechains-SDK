package io.horizen.fork

import io.horizen.utils.Pair

import scala.jdk.CollectionConverters.seqAsJavaListConverter
import java.util

object CustomForkConfiguratorWithConsensusParamsFork {
  def getCustomForkConfiguratorWithConsensusParamsFork(activationHeight: Seq[Int], consensusSlotsPerEpoch: Seq[Int], consensusSecondsPerSlot: Seq[Int]): ForkConfigurator = {
    assert(activationHeight.size == consensusSlotsPerEpoch.size)

    class CustomForkConfigurator extends ForkConfigurator {
      /**
       * Mandatory for every sidechain to provide an epoch number here.
       */
      override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(10, 20, 0)

      override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {

        var optionalSidechainFork: Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = Seq()
        for (i <- activationHeight.indices) {
          optionalSidechainFork = optionalSidechainFork :+ new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](SidechainForkConsensusEpoch(activationHeight(i), activationHeight(i), activationHeight(i)), new ConsensusParamsFork(consensusSlotsPerEpoch(i), consensusSecondsPerSlot(i)))
        }
        optionalSidechainFork.asJava
      }
    }
    new CustomForkConfigurator
  }

}

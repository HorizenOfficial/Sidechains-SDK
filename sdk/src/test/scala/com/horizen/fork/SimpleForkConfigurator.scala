package com.horizen.fork

class SimpleForkConfigurator extends ForkConfigurator {
  override def getBaseSidechainConsensusEpochNumbers(): scConsensusEpochNumber = {
    scConsensusEpochNumber(0, 10, 20)
  }
}
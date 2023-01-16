package com.horizen.examples;

import com.horizen.fork.ForkConfigurator;
import com.horizen.fork.ForkConsensusEpochNumber;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public ForkConsensusEpochNumber getSidechainFork1() {
        return new ForkConsensusEpochNumber(0, 0, 0);
    }
}

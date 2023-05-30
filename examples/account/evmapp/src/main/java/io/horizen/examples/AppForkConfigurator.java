package io.horizen.examples;

import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.ForkConsensusEpochNumber;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public ForkConsensusEpochNumber getSidechainFork1() {
        return new ForkConsensusEpochNumber(0, 0, 0);
    }
}

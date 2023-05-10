package io.horizen.examples;

import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.SidechainForkConsensusEpoch;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch getSidechainFork1() {
        return new SidechainForkConsensusEpoch(3, 3, 3);
    }
}

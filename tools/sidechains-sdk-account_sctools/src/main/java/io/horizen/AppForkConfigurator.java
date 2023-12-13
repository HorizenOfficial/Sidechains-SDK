package io.horizen;

import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.SidechainForkConsensusEpoch;


public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch fork1activation() {
        return new SidechainForkConsensusEpoch(0, 0, 0);
    }
}

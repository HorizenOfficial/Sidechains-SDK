package com.horizen.examples;

import com.horizen.fork.ForkConfigurator;
import com.horizen.fork.scConsensusEpochNumber;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public scConsensusEpochNumber getBaseSidechainConsensusEpochNumbers() {
        return new scConsensusEpochNumber(0,0,0);
    }
}

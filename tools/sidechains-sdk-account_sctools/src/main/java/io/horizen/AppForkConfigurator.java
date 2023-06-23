package io.horizen;

import io.horizen.account.fork.ZenDAOFork;
import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.OptionalSidechainFork;
import io.horizen.fork.SidechainForkConsensusEpoch;
import io.horizen.utils.Pair;

import java.util.List;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch fork1activation() {
        return new SidechainForkConsensusEpoch(0, 0, 0);
    }

    @Override
    public List<Pair<SidechainForkConsensusEpoch, OptionalSidechainFork>> getOptionalSidechainForks() {
        return List.of(
                // TODO
                // this object is used by the bootstrapping tool, if the fork is not active at genesis then any
                // epoch > 0 will do, yet the best thing would be setting the correct fork value for each network type
                new Pair(new SidechainForkConsensusEpoch(1, 1, 1), new ZenDAOFork(true))
        );
    }
}

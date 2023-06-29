package io.horizen.examples;

import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.OptionalSidechainFork;
import io.horizen.fork.Sc2ScFork;
import io.horizen.fork.SidechainForkConsensusEpoch;
import io.horizen.utils.Pair;

import java.util.List;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch fork1activation() {
        return new SidechainForkConsensusEpoch(3, 3, 3);
    }

    @Override
    public List<Pair<SidechainForkConsensusEpoch, OptionalSidechainFork>> getOptionalSidechainForks() {
        // note: the default values for GasFeeFork are automatically enabled on epoch 0
        return List.of(
            new Pair<>(
                new SidechainForkConsensusEpoch(5, 5, 5),
                new Sc2ScFork(true, true)
            )
        );
    }
}

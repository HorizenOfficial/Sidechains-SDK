package io.horizen.examples;

import io.horizen.account.fork.GasFeeFork;
import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.OptionalSidechainFork;
import io.horizen.fork.SidechainForkConsensusEpoch;
import io.horizen.utils.Pair;

import java.math.BigInteger;
import java.util.List;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch fork1activation() {
        return new SidechainForkConsensusEpoch(0, 0, 0);
    }

    @Override
    public List<Pair<SidechainForkConsensusEpoch, OptionalSidechainFork>> getOptionalSidechainForks() {
        // note: the default values for GasFeeFork are automatically enabled on epoch 0
        return List.of(
            new Pair<>(
                new SidechainForkConsensusEpoch(4, 4, 4),
                new GasFeeFork(
                    BigInteger.valueOf(20000000),
                    BigInteger.valueOf(2),
                    BigInteger.valueOf(8),
                    BigInteger.ZERO
                )
            ),
            new Pair<>(
                new SidechainForkConsensusEpoch(5, 5, 5),
                new GasFeeFork(
                    BigInteger.valueOf(25000000),
                    BigInteger.valueOf(2),
                    BigInteger.valueOf(8),
                    BigInteger.ZERO
                )
            )
        );
    }
}

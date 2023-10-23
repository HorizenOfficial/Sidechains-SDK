package io.horizen.examples;

import io.horizen.fork.*;
import io.horizen.account.fork.GasFeeFork;
import io.horizen.account.fork.ZenDAOFork;
import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.OptionalSidechainFork;
import io.horizen.fork.SidechainForkConsensusEpoch;
import io.horizen.utils.Pair;

import java.math.BigInteger;
import java.util.List;

public class AppForkConfigurator extends ForkConfigurator {
    private int gasLimit;
    public AppForkConfigurator(int gasLimit) {
        this.gasLimit = gasLimit;
    }
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
                            BigInteger.valueOf(gasLimit),
                            BigInteger.valueOf(2),
                            BigInteger.valueOf(8),
                            BigInteger.ZERO
                    )
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(7, 7, 7),
                    new ZenDAOFork(true)
            ),
            new Pair<>(
                new SidechainForkConsensusEpoch(15, 15, 15),
                new GasFeeFork(
                    BigInteger.valueOf(gasLimit),
                    BigInteger.valueOf(2),
                    BigInteger.valueOf(8),
                    BigInteger.ZERO
                )
            ),
            new Pair<>(
                   new SidechainForkConsensusEpoch(20, 20, 20),
                   new ConsensusParamsFork(
                           1000,
                           18
                   )
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(30, 30, 30),
                    new ConsensusParamsFork(
                            1500,
                            5
                    )
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(35, 35, 35),
                    new ActiveSlotCoefficientFork(
                            0.05
                    )
            )
        );
    }
}

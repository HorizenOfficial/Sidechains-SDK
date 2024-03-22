package io.horizen.examples;

import io.horizen.account.fork.*;
import io.horizen.fork.*;
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
                    new SidechainForkConsensusEpoch(7, 7, 7),
                    new ZenDAOFork(true)
            ),
            new Pair<>(
                new SidechainForkConsensusEpoch(15, 15, 15),
                new GasFeeFork(
                    BigInteger.valueOf(25000000),
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
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(50, 50, 50),
                    new ContractInteroperabilityFork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(60, 60, 60),
                    new Version1_2_0Fork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(70, 70, 70),
                    new Version1_3_0Fork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(80, 80, 80),
                    new Version1_4_0Fork(true)
            )
        );
    }
}

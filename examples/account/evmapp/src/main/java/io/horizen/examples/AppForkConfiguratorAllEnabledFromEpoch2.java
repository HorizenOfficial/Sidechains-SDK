package io.horizen.examples;

import io.horizen.account.fork.*;
import io.horizen.fork.*;
import io.horizen.utils.Pair;

import java.math.BigInteger;
import java.util.List;

public class AppForkConfiguratorAllEnabledFromEpoch2 extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch fork1activation() {
        return new SidechainForkConsensusEpoch(0, 0, 0);
    }

    @Override
    public List<Pair<SidechainForkConsensusEpoch, OptionalSidechainFork>> getOptionalSidechainForks() {
        // note: the default values for GasFeeFork are automatically enabled on epoch 0
        return List.of(
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new GasFeeFork(
                            BigInteger.valueOf(25000000),
                            BigInteger.valueOf(2),
                            BigInteger.valueOf(8),
                            BigInteger.ZERO
                    )
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new ZenDAOFork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new ConsensusParamsFork(
                            1500,
                            5
                    )
            ),
//This is not enabled because it makes the block creation random
//            new Pair<>(
//                    new SidechainForkConsensusEpoch(35, 35, 35),
//                    new ActiveSlotCoefficientFork(
//                            0.05
//                    )
//            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new ContractInteroperabilityFork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new Version1_2_0Fork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new Version1_3_0Fork(true)
            ),
            new Pair<>(
                    new SidechainForkConsensusEpoch(2, 2, 2),
                    new Version1_4_0Fork(true)
            )
        );
    }
}

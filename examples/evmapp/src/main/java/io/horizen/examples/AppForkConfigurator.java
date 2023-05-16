package io.horizen.examples;

import io.horizen.account.fork.GasFeeFork$;
import io.horizen.fork.ForkConfigurator;
import io.horizen.fork.OptionalSidechainFork;
import io.horizen.fork.SidechainForkConsensusEpoch;
import scala.Predef;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;

import java.util.HashMap;

public class AppForkConfigurator extends ForkConfigurator {
    @Override
    public SidechainForkConsensusEpoch fork1activation() {
        return new SidechainForkConsensusEpoch(0, 0, 0);
    }

    @Override
    public Map<SidechainForkConsensusEpoch, OptionalSidechainFork> getOptionalSidechainForks() {
        var forks = new HashMap<SidechainForkConsensusEpoch, OptionalSidechainFork>();
        forks.put(new SidechainForkConsensusEpoch(0, 0, 0), GasFeeFork$.MODULE$.DefaultGasFeeFork());
        return JavaConverters.mapAsScalaMapConverter(forks).asScala().toMap(Predef.$conforms());
    }
}

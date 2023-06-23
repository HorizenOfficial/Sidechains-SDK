package io.horizen;

import io.horizen.examples.AppForkConfigurator;
import io.horizen.fork.ForkConfigurator;

public class UTXOSimpleAppModel extends AbstractUTXOModel {
    @Override
    public ForkConfigurator getForkConfigurator() {
        return new AppForkConfigurator();
    }
}

package io.horizen;

import io.horizen.fork.ForkConfigurator;

public class UTXOModel extends AbstractUTXOModel {
    @Override
    public ForkConfigurator getForkConfigurator() {
        return new AppForkConfigurator();
    }
}

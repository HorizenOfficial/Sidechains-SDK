package io.horizen;

import io.horizen.account.state.MessageProcessor;
import io.horizen.examples.AppForkConfigurator;
import io.horizen.fork.ForkConfigurator;
import io.horizen.params.NetworkParams;

import java.util.ArrayList;
import java.util.List;

public class AccountSimpleAppModel extends AbstractAccountModel {
    @Override
    protected List<MessageProcessor> getCustomMessageProcessors(NetworkParams params) {
        List<MessageProcessor> customMessageProcessors = new ArrayList<>();
        return customMessageProcessors;
    }

    @Override
    public ForkConfigurator getForkConfigurator() {
        return new AppForkConfigurator();
    }
}

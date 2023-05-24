package io.horizen;

import io.horizen.account.state.MessageProcessor;
import io.horizen.params.NetworkParams;

import java.util.ArrayList;
import java.util.List;

public class AccountModel extends AbstractAccountModel {
    @Override
    protected List<MessageProcessor> getCustomMessageProcessors(NetworkParams params) {
        List<MessageProcessor> customMessageProcessors = new ArrayList<>();
        return customMessageProcessors;
    }
}

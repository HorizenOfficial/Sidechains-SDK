package io.horizen;

import io.horizen.account.sc2sc.ScTxCommitmentTreeRootHashMessageProcessor$;
import io.horizen.account.state.MessageProcessor;
import io.horizen.account.state.MessageProcessorUtil;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.cryptolibprovider.implementations.Sc2scImplZendoo;
import io.horizen.examples.AppForkConfigurator;
import io.horizen.examples.messageprocessor.VoteMessageProcessor;
import io.horizen.examples.messageprocessor.VoteRedeemMessageProcessor;
import io.horizen.fork.ForkConfigurator;
import io.horizen.params.NetworkParams;
import io.horizen.utils.BytesUtils;

import java.util.ArrayList;
import java.util.List;

public class AccountSimpleAppModel extends AbstractAccountModel {
    @Override
    protected List<MessageProcessor> getCustomMessageProcessors(NetworkParams params) {
        ScTxCommitmentTreeRootHashMessageProcessor$ scTxMsgProc = MessageProcessorUtil.getScTxMsgProc();
        List<MessageProcessor> customMessageProcessors = new ArrayList<>();
        byte[] scId = BytesUtils.reverseBytes(params.sidechainId());
        Sc2scCircuit circuit = new Sc2scImplZendoo();
        customMessageProcessors.add(new VoteMessageProcessor(scId));
        customMessageProcessors.add(new VoteRedeemMessageProcessor(scId, params.sc2ScVerificationKeyFilePath(), circuit, scTxMsgProc));
        return customMessageProcessors;
    }

    @Override
    public ForkConfigurator getForkConfigurator() {
        return new AppForkConfigurator();
    }
}

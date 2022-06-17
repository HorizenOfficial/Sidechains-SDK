package com.horizen.examples;

import com.horizen.account.state.*;
import com.horizen.evm.Evm;

public class EvmMessageProcessor implements MessageProcessor {
    @Override
    public void init(AccountStateView view) {
        // nothing to do here
    }

    /**
     * Can process messages that require an EVM invocation:
     * <ol>
     * <li>when "to" address is empty; deploying a new contract</li>
     * <li>when the account at the "to" address has non-empty code; invoke smart contract</li>
     * </ol>
     */
    @Override
    public boolean canProcess(Message msg, AccountStateView view) {
        return msg.getTo() == null || view.getCodeHash(msg.getTo().address());
    }

    @Override
    public ExecutionResult process(Message msg, AccountStateView view) {
        try {
            // TODO: get stateDB
            var result = Evm.Apply(
                null,
                msg.getFrom().address(),
                msg.getTo().address(),
                msg.getValue(),
                msg.getData(),
                msg.getGasLimit(),
                msg.getGasPrice()
            );
            if (result.evmError.isEmpty()) {
                return new ExecutionSucceeded(result.usedGas, result.returnData);
            }
            return new ExecutionFailed(result.usedGas, new Exception(result.evmError));
        } catch (Exception err) {
            return new InvalidMessage(err);
        }
    }
}

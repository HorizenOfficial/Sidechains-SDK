package com.horizen.examples;

import com.horizen.account.state.*;
import com.horizen.evm.Evm;
import scorex.crypto.hash.Keccak256;

import java.util.Arrays;

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
        // contract deployment to a new account
        if (msg.getTo() == null) return true;
        var to = msg.getTo().address();
        return view.isSmartContractAccount(to);
    }

    @Override
    public ExecutionResult process(Message msg, AccountStateView view) {
        try {
            var result = Evm.Apply(
                view.stateDb(),
                msg.getFrom().address(),
                msg.getTo() == null ? null : msg.getTo().address(),
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

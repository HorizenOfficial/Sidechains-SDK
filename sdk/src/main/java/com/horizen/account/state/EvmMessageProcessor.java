package com.horizen.account.state;

import com.horizen.evm.Evm;
import com.horizen.evm.interop.EvmContext;

import java.math.BigInteger;

public class EvmMessageProcessor implements MessageProcessor {
    @Override
    public void init(BaseAccountStateView view) {
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
    public boolean canProcess(Message msg, BaseAccountStateView view) {
        // contract deployment to a new account
        if (msg.getTo() == null) return true;
        var to = msg.getTo().address();
        return view.isSmartContractAccount(to);
    }

    @Override
    public ExecutionResult process(Message msg, BaseAccountStateView view) {
        try {
            // prepare context
            var context = new EvmContext();
            context.baseFee = view.getBaseFee();
            context.blockNumber = BigInteger.valueOf(view.getHeight());
            // execute EVM
            var result = Evm.Apply(
                    view.getStateDbHandle(),
                    msg.getFrom().address(),
                    msg.getTo() == null ? null : msg.getTo().address(),
                    msg.getValue(),
                    msg.getData(),
                    // TODO: the GASLIMIT opcode will return wrong results this way, because the intrinsicGas was already removed here
                    view.getGasPool().getAvailableGas(),
                    msg.getGasPrice(),
                    context
            );
            // consume gas the EVM has used:
            // the EVM will never consume more gas than is available, hence this should never throw
            view.getGasPool().consumeGas(result.usedGas);
            if (result.evmError.isEmpty()) {
                return new ExecutionSucceeded(result.returnData);
            }
            return new ExecutionFailed(new EvmException(result.evmError, result.returnData));
        } catch (Exception err) {
            return new InvalidMessage(err);
        }
    }
}

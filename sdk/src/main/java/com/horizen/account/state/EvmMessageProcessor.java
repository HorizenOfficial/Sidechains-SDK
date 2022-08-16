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
    public byte[] process(Message msg, BaseAccountStateView view) throws ExecutionFailedException {
        // prepare context
        var context = new EvmContext();
        context.baseFee = view.getBaseFee();
        context.blockNumber = BigInteger.valueOf(view.getHeight());
        // total gas limit supplied in the transaction, only used by the GASLIMIT opcode
        // current gas in the pool cannot be used because intrinsic gas was already removed here
        context.gasLimit = msg.getGasLimit();
        // execute EVM
        var result = Evm.Apply(
                view.getStateDbHandle(),
                msg.getFrom().address(),
                msg.getTo() == null ? null : msg.getTo().address(),
                msg.getValue(),
                msg.getData(),
                view.getGas(),
                msg.getGasPrice(),
                context
        );
        // consume gas the EVM has used:
        // the EVM will never consume more gas than is available, hence this should never throw
        // and OutOfGasException is manually thrown if the EVM reported "out of gas"
        view.subGas(result.usedGas);
        if (!result.evmError.isEmpty()) {
            if (result.evmError.startsWith("out of gas")) {
                throw new OutOfGasException();
            }
            // returnData here will include the revert reason
            throw new EvmException(result.evmError, result.returnData);
        }
        return result.returnData;
    }
}

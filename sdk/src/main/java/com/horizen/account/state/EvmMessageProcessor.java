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
    public byte[] process(Message msg, BaseAccountStateView view, GasPool gas) throws ExecutionFailedException {
        // prepare context
        var context = new EvmContext();
        // TODO: baseFee will be passed within message - until then it is 0
        context.baseFee = BigInteger.ZERO;
        // TODO: we need versioning for the block height:
        //  getHeight() currently always returns the block number of the latest block, independent of the state we're in
        //  this might lead to different results when replaying a transaction
        context.blockNumber = BigInteger.valueOf(view.getHeight()).add(BigInteger.ONE);
        context.gasLimit = view.getBlockGasLimit();
        // execute EVM
        var result = Evm.Apply(
                view.getStateDbHandle(),
                msg.getFrom().address(),
                msg.getTo() == null ? null : msg.getTo().address(),
                msg.getValue(),
                msg.getData(),
                // use gas from the pool not the message, because intrinsic gas was already spent at this point
                gas.getGas(),
                msg.getGasPrice(),
                context
        );
        var returnData = result.returnData == null ? new byte[0] : result.returnData;
        // consume gas the EVM has used:
        // the EVM will never consume more gas than is available, hence this should never throw
        // and OutOfGasException is manually thrown if the EVM reported "out of gas"
        gas.subGas(result.usedGas);
        if (!result.evmError.isEmpty()) {
            switch (result.evmError) {
                case "execution reverted":
                    // returnData here will include the revert reason
                    throw new ExecutionRevertedException(returnData);
                case "out of gas":
                    throw new OutOfGasException();
                default:
                    throw new ExecutionFailedException(result.evmError);
            }
        }
        return returnData;
    }
}

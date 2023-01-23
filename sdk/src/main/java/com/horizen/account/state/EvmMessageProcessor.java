package com.horizen.account.state;

import com.horizen.evm.Evm;
import com.horizen.evm.interop.EvmContext;
import com.horizen.evm.utils.Address;

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
        if (msg.getTo().isEmpty()) return true;
        var to = msg.getTo().get().address();
        return view.isSmartContractAccount(to);
    }

    @Override
    public byte[] process(Message msg, BaseAccountStateView view, GasPool gas, BlockContext blockContext)
            throws ExecutionFailedException {
        // prepare context
        var context = new EvmContext();

        context.chainID = BigInteger.valueOf(blockContext.chainID);
        context.coinbase = Address.fromBytes(blockContext.forgerAddress);
        context.gasLimit = BigInteger.valueOf(blockContext.blockGasLimit);
        context.blockNumber = BigInteger.valueOf(blockContext.blockNumber);
        context.time = BigInteger.valueOf(blockContext.timestamp);
        context.baseFee = blockContext.baseFee;
        // TODO: add 32 bytes of random from VRF to support the "PREVRANDAO" (ex "DIFFICULTY") EVM-opcode
//        context.random = null;
        // execute EVM
        var result = Evm.Apply(
                view.getStateDbHandle(),
                msg.getFromAddressBytes(),
                msg.getToAddressBytes(),
                msg.getValue(),
                msg.getData(),
                // use gas from the pool not the message, because intrinsic gas was already spent at this point
                gas.getGas(),
                msg.getGasPrice(),
                context,
                blockContext.getTraceParams()
        );
        blockContext.setEvmResult(result);
        var returnData = result.returnData == null ? new byte[0] : result.returnData;
        // consume gas the EVM has used:
        // the EVM will never consume more gas than is available, hence this should never throw
        // and ExecutionFailedException is thrown if the EVM reported "out of gas"
        gas.subGas(result.usedGas);
        if (result.reverted) throw new ExecutionRevertedException(returnData);
        if (!result.evmError.isEmpty()) throw new ExecutionFailedException(result.evmError);
        return returnData;
    }
}

package com.horizen.account.state;

import com.horizen.evm.BlockHashCallback;
import com.horizen.evm.Evm;
import com.horizen.evm.EvmContext;
import com.horizen.evm.Hash;
import com.horizen.utils.BytesUtils;
import scala.compat.java8.OptionConverters;

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
        var to = msg.getTo();
        // contract deployment to a new account
        if (to.isEmpty()) return true;
        return view.isSmartContractAccount(to.get());
    }

    @Override
    public byte[] process(Message msg, BaseAccountStateView view, GasPool gas, BlockContext blockContext)
        throws ExecutionFailedException {
        // prepare context
        var context = new EvmContext();
        context.chainID = BigInteger.valueOf(blockContext.chainID);
        context.coinbase = blockContext.forgerAddress;
        context.gasLimit = blockContext.blockGasLimit;
        context.blockNumber = BigInteger.valueOf(blockContext.blockNumber);
        context.time = BigInteger.valueOf(blockContext.timestamp);
        context.baseFee = blockContext.baseFee;
        context.random = blockContext.random;

        // setup callback for the evm to access the block hash provider
        try (var blockHashGetter = new BlockHashGetter(blockContext.blockHashProvider)) {
            context.blockHashCallback = blockHashGetter;

            // execute EVM
            var result = Evm.Apply(
                view.getStateDbHandle(),
                msg.getFrom(),
                msg.getTo().orElse(null),
                msg.getValue(),
                msg.getData(),
                // use gas from the pool not the message, because intrinsic gas was already spent at this point
                gas.getGas(),
                msg.getGasPrice(),
                context,
                blockContext.getTraceOptions()
            );
            blockContext.setEvmResult(result);
            // consume gas the EVM has used:
            // the EVM will never consume more gas than is available, hence this should never throw
            // and ExecutionFailedException is thrown if the EVM reported "out of gas"
            gas.subGas(result.usedGas);
            if (result.reverted) throw new ExecutionRevertedException(result.returnData);
            if (!result.evmError.isEmpty()) throw new ExecutionFailedException(result.evmError);
            return result.returnData;
        }
    }

    private static class BlockHashGetter extends BlockHashCallback {
        private final HistoryBlockHashProvider provider;

        private BlockHashGetter(HistoryBlockHashProvider provider) {
            this.provider = provider;
        }

        @Override
        protected Hash getBlockHash(BigInteger blockNumber) {
            return OptionConverters
                .toJava(provider.blockIdByHeight(blockNumber.intValueExact()))
                .map(hex -> new Hash(BytesUtils.fromHexString(hex)))
                .orElse(Hash.ZERO);
        }
    }
}

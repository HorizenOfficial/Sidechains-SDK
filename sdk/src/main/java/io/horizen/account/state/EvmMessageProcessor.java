package io.horizen.account.state;

import io.horizen.account.utils.WellKnownAddresses;
import io.horizen.evm.*;
import io.horizen.utils.BytesUtils;
import scala.Option;
import scala.compat.java8.OptionConverters;

import java.math.BigInteger;

public class EvmMessageProcessor implements MessageProcessor {
    private static final Address[] nativeContractAddresses = new Address[] {
        WellKnownAddresses.WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS(),
        WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS(),
    };

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
    public boolean canProcess(Invocation invocation, BaseAccountStateView view) {
        var to = invocation.callee();
        // contract deployment to a new account
        if (to.isEmpty()) return true;
        return view.isSmartContractAccount(to.get());
    }

    @Override
    public byte[] process(Invocation invocation, BaseAccountStateView view, ExecutionContext context)
        throws ExecutionFailedException {
        // prepare context
        var block = context.blockContext();
        var evmContext = new EvmContext();
        evmContext.chainID = BigInteger.valueOf(block.chainID);
        evmContext.coinbase = block.forgerAddress;
        evmContext.gasLimit = block.blockGasLimit;
        evmContext.blockNumber = BigInteger.valueOf(block.blockNumber);
        evmContext.time = BigInteger.valueOf(block.timestamp);
        evmContext.baseFee = block.baseFee;
        evmContext.random = block.random;

        // setup callback for the evm to access the block hash provider
        try (
            var blockHashGetter = new BlockHashGetter(block.blockHashProvider);
            var nativeContractProxy = new NativeContractProxy(context)
        ) {
            evmContext.blockHashCallback = blockHashGetter;
            evmContext.externalContracts = nativeContractAddresses;
            evmContext.externalCallback = nativeContractProxy;
            evmContext.tracer = block.getTracer();

            // execute EVM
            var result = Evm.Apply(
                view.getStateDbHandle(),
                invocation.caller(),
                invocation.callee().getOrElse(() -> null),
                invocation.value(),
                invocation.input(),
                invocation.gasPool().getGas(),
                context.msg().getGasPrice(),
                evmContext
            );
            // consume gas the EVM has used:
            // the EVM will never consume more gas than is available, hence consuming used gas here should never throw,
            // instead the EVM will report an "out of gas" error which we throw as an ExecutionFailedException
            invocation.gasPool().subGas(result.usedGas);
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

    private static class NativeContractProxy extends InvocationCallback {
        private final ExecutionContext context;

        public NativeContractProxy(ExecutionContext context) {
            this.context = context;
        }

        @Override
        protected InvocationResult execute(io.horizen.evm.Invocation invocation) {
            var gasPool = new GasPool(invocation.gas);
            try {
                var returnData = context.execute(
                    Invocation.apply(
                        invocation.caller,
                        Option.apply(invocation.callee),
                        invocation.value,
                        invocation.input,
                        gasPool,
                        invocation.readOnly
                    )
                );
                return new InvocationResult(returnData, gasPool.getGas(), "");
            } catch (ExecutionRevertedException e) {
                // forward the revert reason if any
                return new InvocationResult(e.returnData, gasPool.getGas(), e.getMessage());
            } catch (Exception e) {
                return new InvocationResult(new byte[0], gasPool.getGas(), e.getMessage());
            }
        }
    }
}

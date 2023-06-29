package io.horizen.account.state;

import io.horizen.account.utils.WellKnownAddresses;
import io.horizen.evm.*;
import io.horizen.utils.BytesUtils;
import scala.Array;
import scala.Option;
import scala.compat.java8.OptionConverters;

import java.math.BigInteger;

public class EvmMessageProcessor implements MessageProcessor {
    protected Address[] nativeContractAddresses = new Address[] {
        WellKnownAddresses.WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS(),
        WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS(),
    };

    @Override
    public boolean customTracing() {
        // the EVM handles all calls to the tracer, if there is one
        return true;
    }

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
        evmContext.gasPrice = context.msg().getGasPrice();
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
            evmContext.tracer = block.getTracer().orElse(null);
            // Minus one because the depth is incremented for the call to the EvmMessageProcessor itself.
            // We want to ignore that as the EVM will increment depth immediately for the first call frame.
            // Basically, the depth would be incremented twice for the first EVM-based call frame without this.
            evmContext.initialDepth = context.depth() - 1;

            // transform to libevm Invocation type
            var evmInvocation = new io.horizen.evm.Invocation(
                invocation.caller(),
                invocation.callee().getOrElse(() -> null),
                invocation.value(),
                invocation.input(),
                invocation.gasPool().getGas(),
                invocation.readOnly()
            );

            // execute EVM
            var result = Evm.Apply(view.getStateDbHandle(), evmInvocation, evmContext);

            // consume gas the EVM has used:
            // the EVM will never consume more gas than is available, hence consuming used gas here should never throw,
            // instead the EVM will report an "out of gas" error which we throw as an ExecutionFailedException
            var usedGas = invocation.gasPool().getGas().subtract(result.leftOverGas);
            invocation.gasPool().subGas(usedGas);
            if (result.reverted) throw new ExecutionRevertedException(result.returnData);
            if (!result.executionError.isEmpty()) throw new ExecutionFailedException(result.executionError);
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

        /**
         * Returns exception.toString(), but makes sure the return value is non-null and non-empty. If e.g. toString()
         * is overriden in a custom exception and returns null this will return the exceptions class name instead.
         */
        private String nonEmptyErrorMessage(Exception exception) {
            var msg = exception.toString();
            if (msg == null || msg.isEmpty()) {
                msg = exception.getClass().getName();
            }
            return msg;
        }

        @Override
        protected InvocationResult execute(ExternalInvocation invocation) {
            var gasPool = new GasPool(invocation.gas);
            try {
                var returnData = context.executeDepth(
                    // transform to SDK Invocation type
                    Invocation.apply(
                        invocation.caller,
                        Option.apply(invocation.callee),
                        invocation.value,
                        Option.apply(invocation.input).getOrElse(Array::emptyByteArray),
                        gasPool,
                        invocation.readOnly
                    ),
                    // advance call depth by the call depth processed by the EVM
                    invocation.depth - 1
                );
                return new InvocationResult(returnData, gasPool.getGas(), "", false, null);
            } catch (ExecutionRevertedException e) {
                // forward the revert reason if any
                return new InvocationResult(e.returnData, gasPool.getGas(), nonEmptyErrorMessage(e), true, null);
            } catch (Exception e) {
                return new InvocationResult(new byte[0], gasPool.getGas(), nonEmptyErrorMessage(e), false, null);
            }
        }
    }
}

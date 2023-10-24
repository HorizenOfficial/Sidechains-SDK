package io.horizen.account.state;


// This interface models the entity which is responsible for handling the application of a transaction to a state view.
// More in detail, a transaction is converted into a 'Message' object, which is processed
// by a specific instance of MessageProcessor.
// The specific instance of MessageProcessor is selected by looping on a list (initialized
// at genesis state creation) and executing the method 'canProcess'.
// Currently, there are 3 main MessageProcessor types:
//  - Eoa2Eoa: handling regular coin transfers between EOA accounts
//  - Evm: handling transactions requiring EVM invocations (such as smart contract deployment/invocation/...)
//  - NativeSmartContract: Handling SC custom logic not requiring EVM invocations (Forger Stake handling, Withdrawal request ...)
// It is possible to extend the MessageProcessors list in the application level by adding custom instances
public interface MessageProcessor {
    // Initialization is going to happen only once at genesis State creation.
    // Common pattern: declare a new native smart contract account in the View
    void init(BaseAccountStateView view, int consensusEpochNumber) throws MessageProcessorInitializationException;

    boolean customTracing();

    // Checks if the processor is applicable to the invocation. Some message processor can support messages when reaching
    // a fork point, therefore we pass along the consensus epoch number, which is not stored in stateDb
    boolean canProcess(Invocation invocation, BaseAccountStateView view, int consensusEpochNumber);

    /**
     * Apply invocation to the given view. Possible results:
     * <ul>
     *     <li>applied as expected: return byte[]</li>
     *     <li>invocation valid and (partially) executed, but operation "failed": throw ExecutionFailedException</li>
     *     <li>invocation invalid and must not exist in a block: throw any other Exception</li>
     * </ul>
     *
     * @param invocation invocation to execute
     * @param view       state view
     * @param context    contextual information accessible during execution. It contains also the consensus epoch number
     * @return return data on successful execution
     * @throws ExecutionRevertedException revert-and-keep-gas-left, also mark the message as "failed"
     * @throws ExecutionFailedException   revert-and-consume-all-gas, also mark the message as "failed"
     * @throws RuntimeException           any other exceptions are considered as "invalid message"
     */
    byte[] process(Invocation invocation, BaseAccountStateView view, ExecutionContext context)
        throws ExecutionFailedException;
}

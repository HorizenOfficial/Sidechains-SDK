package io.horizen.account.state;


// This interface models the entity which is responsible for handling the application of a transaction to a state view.
// More in detail, a transaction is converted into a 'Message' object, which is processed
// by a specific instance of MessageProcessor.
// The specific instance of MessageProcessor is selected by looping on a list (initialized
// at genesis state creation) and executing the method 'canProcess'.
// Currently there are 3 main MessageProcessor types:
//  - Eoa2Eoa: handling regular coin transfers between EOA accounts
//  - Evm: handling transactions requiring EVM invocations (such as smart contract deployment/invocation/...)
//  - NativeSmartContract: Handling SC custom logic not requiring EVM invocations (Forger Stake handling, Withdrawal request ...)
// It is possible to extend the MessageProcessors list in the application level by adding custom instances
public interface MessageProcessor {
    // Initialization is going to happen only once at genesis State creation.
    // Common pattern: declare a new native smart contract account in the View
    void init(BaseAccountStateView view) throws MessageProcessorInitializationException;

    // Checks if the processor is applicable to the Message
    boolean canProcess(Message msg, BaseAccountStateView view);

    /**
     * Apply message to the given view. Possible results:
     * <ul>
     *     <li>applied as expected: return byte[]</li>
     *     <li>message valid and (partially) executed, but operation "failed": throw ExecutionFailedException</li>
     *     <li>message invalid and must not exist in a block: throw any other Exception</li>
     * </ul>
     *
     * @param msg message to apply to the state
     * @param view state view
     * @param gas available gas for the execution
     * @param blockContext contextual information accessible during execution
     * @return return data on successful execution
     * @throws ExecutionRevertedException revert-and-keep-gas-left, also mark the message as "failed"
     * @throws ExecutionFailedException revert-and-consume-all-gas, also mark the message as "failed"
     * @throws RuntimeException any other exceptions are consideres as "invalid message"
     */
    byte[] process(Message msg, BaseAccountStateView view, GasPool gas, BlockContext blockContext)
            throws ExecutionFailedException;
}

package com.horizen.account.state;

public interface MessageProcessor {
    // Initialization is going to happen only once at genesis State creation.
    // Common pattern: declare a new fake smart contract account in the View
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

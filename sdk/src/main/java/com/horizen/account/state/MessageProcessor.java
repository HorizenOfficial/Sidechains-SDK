package com.horizen.account.state;

public interface MessageProcessor {
    // Initialization is going to happen only once at genesis State creation.
    // Common pattern: declare a new fake smart contract account in the View
    void init(BaseAccountStateView view) throws MessageProcessorInitializationException;

    // Checks if the processor is applicable to the Message
    boolean	canProcess(Message msg, BaseAccountStateView view);

    // Apply processor modifying the view.
    // Possible results:
    // * return byte[] returnData -> if was applied as expected
    // * throws ExecutionFailedException -> if was executed, but marked as "failed"
    // * throws any other Exception -> if is invalid -> block is invalid
    byte[] process(Message msg, BaseAccountStateView view, GasPool gas) throws ExecutionFailedException;
}

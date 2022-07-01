package com.horizen.account.state;

public interface MessageProcessor {
    // Initialization is going to happen only once at genesis State creation.
    // Common pattern: declare a new fake smart contract account in the View
    void init(BaseAccountStateView view) throws MessageProcessorInitializationException;

    // Checks if the processor is applicable to the Message
    boolean	canProcess(Message msg, BaseAccountStateView view);

    // Apply processor modifying the view.
    // Possible results:
    // * ExecutionSucceeded(BigInteger gasUsed, byte[] returnData) -> if was applied as expected
    // * ExecutionFailed(BigInteger gasUsed, Exception reason) -> if was executed, but marked as "failed"
    // * InvalidMessage(Exception reason) -> if is invalid -> block is invalid
    ExecutionResult process(Message msg, BaseAccountStateView view);
}

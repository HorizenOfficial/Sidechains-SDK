package com.horizen.account.state;

/**
 * Not enough gas remaining to continue execution.
 */
public class OutOfGasException extends ExecutionFailedException {
    public OutOfGasException() {
        super("out of gas");
    }
}

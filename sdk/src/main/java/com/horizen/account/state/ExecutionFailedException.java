package com.horizen.account.state;

/**
 * Message processing failed, alos revert-and-consume-all-gas.
 */
public class ExecutionFailedException extends Exception {
    public ExecutionFailedException(String message) {
        super(message);
    }

    public ExecutionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

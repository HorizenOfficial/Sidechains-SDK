package io.horizen.account.state;

/**
 * Message processing failed, also revert-and-consume-all-gas.
 */
public class ExecutionFailedException extends Exception {
    public ExecutionFailedException(String message) {
        super(message);
    }

    public ExecutionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

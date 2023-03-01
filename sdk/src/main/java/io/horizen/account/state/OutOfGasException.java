package io.horizen.account.state;

/**
 * Not enough gas remaining to continue execution.
 */
public class OutOfGasException extends ExecutionFailedException {

    public OutOfGasException(String errorMessage) {
        super(errorMessage);
    }

    public OutOfGasException(String message, Throwable cause) {
        super(message, cause);
    }

}

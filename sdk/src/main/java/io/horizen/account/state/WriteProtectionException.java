package io.horizen.account.state;

public class WriteProtectionException extends ExecutionFailedException {
    public WriteProtectionException(String message) {
        super(message);
    }
}

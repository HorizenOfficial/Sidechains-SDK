package com.horizen.account.state;

// Message was executed, but some runtime error occurred.
// For example, not enough gas or message data doesn't satisfy current state.
public class ExecutionFailed implements ExecutionResult {
    private final Exception reason;

    public ExecutionFailed(Exception reason) {
        this.reason = reason;
    }

    @Override
    public boolean isFailed() {
        return true;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public Exception getReason() {
        return reason;
    }
}

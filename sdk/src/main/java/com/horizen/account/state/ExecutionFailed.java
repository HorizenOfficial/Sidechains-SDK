package com.horizen.account.state;

import java.math.BigInteger;


// Message was executed, but some runtime error occurred.
// For example, not enough gas or message data doesn't satisfy current state.
public class ExecutionFailed implements ExecutionResult {
    private final BigInteger gasUsed; // Total used gas during Message processing
    private final Exception reason;

    public ExecutionFailed(BigInteger gasUsed, Exception reason) {
        this.gasUsed = gasUsed;
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

    @Override
    public BigInteger gasUsed() {
        return gasUsed;
    }

    public Exception getReason() {
        return reason;
    }
}
